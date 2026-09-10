package com.Coritech.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Coritech.beans.Booking;
import com.Coritech.beans.Journey;
import com.Coritech.beans.User;
import com.Coritech.exception.BookingConflictException;
import com.Coritech.exception.ForbiddenException;
import com.Coritech.exception.ResourceNotFoundException;
import com.Coritech.exception.SeatsUnavailableException;
import com.Coritech.outbox.BookingEventPayload;
import com.Coritech.outbox.OutboxEvent;
import com.Coritech.outbox.OutboxEventRepository;
import com.Coritech.repository.BookingRepository;
import com.Coritech.repository.JourneyRepository;
import com.Coritech.repository.UserRepository;
import com.Coritech.service.BookingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

	private static final int MAX_ATTEMPTS = 8;

	private final JourneyRepository journeyRepository;
	private final BookingRepository bookingRepository;
	private final UserRepository userRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;

	// Self-injected proxy so each retry attempt below runs in its own fresh
	// @Transactional (a call from bookTickets to attemptBooking on "this"
	// would bypass the Spring transaction proxy entirely). Typed to the impl
	// (not the BookingService interface) because attemptBooking/attemptCancel
	// are internal retry helpers, not part of the public service contract.
	// Kept as field injection (not a @RequiredArgsConstructor parameter):
	// it's non-final and @Lazy specifically to break the circular
	// dependency of a bean depending on its own proxy.
	@Autowired
	@Lazy
	private BookingServiceImpl self;

	@Override
	public Booking bookTickets(Long userId, Long journeyId, int noOfTickets, String idempotencyKey) {
		if (noOfTickets <= 0) {
			throw new IllegalArgumentException("noOfTickets must be positive");
		}
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				return self.attemptBooking(userId, journeyId, noOfTickets, idempotencyKey);
			} catch (ObjectOptimisticLockingFailureException conflict) {
				if (attempt == MAX_ATTEMPTS) {
					log.warn("Booking conflict exhausted all {} attempts for journey {} (user {})", MAX_ATTEMPTS,
							journeyId, userId);
					throw new BookingConflictException(
							"Seats for journey " + journeyId + " were booked by someone else, please retry");
				}
				backoffBeforeRetry(attempt);
			}
		}
		throw new BookingConflictException("Could not book journey " + journeyId + ", please retry");
	}

	@Transactional
	public Booking attemptBooking(Long userId, Long journeyId, int noOfTickets, String idempotencyKey) {
		if (idempotencyKey != null && !idempotencyKey.isBlank()) {
			Optional<Booking> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
			if (existing.isPresent()) {
				return existing.get();
			}
		}

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
		Journey journey = journeyRepository.findById(journeyId)
				.orElseThrow(() -> new ResourceNotFoundException("Journey not found: " + journeyId));

		if (journey.getTicketsRemaining() < noOfTickets) {
			throw new SeatsUnavailableException("Not enough seats remaining for journey " + journeyId);
		}

		journey.setTicketsRemaining(journey.getTicketsRemaining() - noOfTickets);
		// Flush now (not at commit) so a concurrent update is detected as an
		// ObjectOptimisticLockingFailureException here, inside this attempt,
		// rather than surfacing later at transaction commit.
		journeyRepository.saveAndFlush(journey);

		Booking booking = new Booking();
		booking.setUser(user);
		booking.setJourney(journey);
		booking.setNoOfTicketsBooked(noOfTickets);
		booking.setIdempotencyKey(idempotencyKey);

		try {
			Booking saved = bookingRepository.saveAndFlush(booking);
			log.info("Booked {} ticket(s) on journey {} for user {} (booking {})", noOfTickets, journeyId, userId,
					saved.getBookingId());
			recordOutboxEvent("BookingConfirmed", saved);
			return saved;
		} catch (DataIntegrityViolationException duplicateKey) {
			// Another concurrent request with the same idempotency key won the race.
			return bookingRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> duplicateKey);
		}
	}

	@Override
	public void cancelTickets(Long userId, Long bookingId) {
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				self.attemptCancel(userId, bookingId);
				return;
			} catch (ObjectOptimisticLockingFailureException conflict) {
				if (attempt == MAX_ATTEMPTS) {
					throw new BookingConflictException(
							"Could not cancel booking " + bookingId + " due to concurrent updates, please retry");
				}
				backoffBeforeRetry(attempt);
			}
		}
	}

	// Small randomized (jittered) backoff between optimistic-lock retries, so
	// competing threads don't immediately collide again on the very next
	// attempt. Without this, a thread can exhaust MAX_ATTEMPTS purely from bad
	// scheduling luck under heavy contention even while seats remain.
	private void backoffBeforeRetry(int attempt) {
		try {
			Thread.sleep(ThreadLocalRandom.current().nextInt(5, 15 + attempt * 10));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Transactional
	public void attemptCancel(Long userId, Long bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

		if (!booking.getUser().getUserId().equals(userId)) {
			throw new ForbiddenException("You do not have permission to cancel booking " + bookingId);
		}

		if (booking.isCancelled()) {
			// Already cancelled: no-op, so cancelling twice never double-refunds seats.
			return;
		}

		Journey journey = booking.getJourney();
		journey.setTicketsRemaining(journey.getTicketsRemaining() + booking.getNoOfTicketsBooked());
		journeyRepository.saveAndFlush(journey);

		booking.setCancelled(true);
		bookingRepository.save(booking);
		log.info("Cancelled booking {} for user {}, refunded {} seat(s) to journey {}", bookingId, userId,
				booking.getNoOfTicketsBooked(), journey.getJourneyId());
		recordOutboxEvent("BookingCancelled", booking);
	}

	// Writes the outbox row in the same transaction as the business change
	// above it, which is the entire point of the pattern: the event can never
	// be committed without the change, or vice versa. If serialization fails
	// here, the whole transaction (booking included) rolls back rather than
	// silently dropping the event.
	private void recordOutboxEvent(String eventType, Booking booking) {
		BookingEventPayload payload = new BookingEventPayload(booking.getBookingId(), booking.getUser().getUserId(),
				booking.getJourney().getJourneyId(), booking.getNoOfTicketsBooked(), booking.isCancelled());
		String json;
		try {
			json = objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize outbox event payload for booking "
					+ booking.getBookingId(), e);
		}
		outboxEventRepository.save(new OutboxEvent("Booking", booking.getBookingId(), eventType, json));
	}

	@Override
	public Page<Booking> getTicketsBookedByUserForEachCity(Long userId, Pageable pageable) {
		return bookingRepository.findByUserUserId(userId, pageable);
	}

}
