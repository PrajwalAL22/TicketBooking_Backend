package com.Coritech.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.Coritech.beans.Booking;

public interface BookingService {

	Booking bookTickets(Long userId, Long journeyId, int noOfTickets, String idempotencyKey);

	// noOfTickets == null cancels every still-active ticket on the booking
	// (idempotent: a no-op once fully cancelled). An explicit count cancels
	// just that many and is not idempotent — each call cancels more.
	void cancelTickets(Long userId, Long bookingId, Integer noOfTickets);

	default void cancelTickets(Long userId, Long bookingId) {
		cancelTickets(userId, bookingId, null);
	}

	Page<Booking> getTicketsBookedByUserForEachCity(Long userId, Pageable pageable);

}
