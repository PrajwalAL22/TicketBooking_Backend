package com.Coritech.service.impl;

import java.util.List;
import java.util.Optional;

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
import com.Coritech.exception.ResourceNotFoundException;
import com.Coritech.exception.SeatsUnavailableException;
import com.Coritech.repository.BookingRepository;
import com.Coritech.repository.JourneyRepository;
import com.Coritech.repository.UserRepository;
import com.Coritech.service.BookingService;

@Service
public class BookingServiceImpl implements BookingService {

	private static final int MAX_ATTEMPTS = 3;

	@Autowired
	private JourneyRepository journeyRepository;
	@Autowired
	private BookingRepository bookingRepository;
	@Autowired
	private UserRepository userRepository;

	// Self-injected proxy so each retry attempt below runs in its own fresh
	// @Transactional (a call from bookTickets to attemptBooking on "this"
	// would bypass the Spring transaction proxy entirely). Typed to the impl
	// (not the BookingService interface) because attemptBooking/attemptCancel
	// are internal retry helpers, not part of the public service contract.
	@Autowired
	@Lazy
	private BookingServiceImpl self;

	@Override
	public Booking bookTickets(int userId, int journeyId, int noOfTickets, String idempotencyKey) {
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
			}
		}
		throw new BookingConflictException("Could not book journey " + journeyId + ", please retry");
	}

	@Transactional
	public Booking attemptBooking(int userId, int journeyId, int noOfTickets, String idempotencyKey) {
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
	public void cancelTickets(int bookingId) {
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				self.attemptCancel(bookingId);
				return;
			} catch (ObjectOptimisticLockingFailureException conflict) {
				if (attempt == MAX_ATTEMPTS) {
					throw new BookingConflictException(
							"Could not cancel booking " + bookingId + " due to concurrent updates, please retry");
				}
			}
		}
	}

	@Transactional
	public void attemptCancel(int bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

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
	public List<Booking> getTicketsBookedByUserForEachCity(int userId) {
		return bookingRepository.findByUserUserId(userId);
	}

}
