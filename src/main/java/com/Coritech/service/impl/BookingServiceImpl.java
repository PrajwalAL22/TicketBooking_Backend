package com.Coritech.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.Coritech.repository.BookingRepository;
import com.Coritech.repository.JourneyRepository;
import com.Coritech.repository.UserRepository;
import com.Coritech.service.BookingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

	private static final int MAX_ATTEMPTS = 8;

	private final JourneyRepository journeyRepository;
	private final BookingRepository bookingRepository;
	private final UserRepository userRepository;

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
			return bookingRepository.saveAndFlush(booking);
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
	}

	@Override
	public List<Booking> getTicketsBookedByUserForEachCity(Long userId) {
		return bookingRepository.findByUserUserId(userId);
	}

}
