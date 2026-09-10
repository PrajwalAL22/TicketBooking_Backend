package com.Coritech.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.Coritech.beans.Booking;

public interface BookingService {

	Booking bookTickets(Long userId, Long journeyId, int noOfTickets, String idempotencyKey);

	void cancelTickets(Long userId, Long bookingId);

	Page<Booking> getTicketsBookedByUserForEachCity(Long userId, Pageable pageable);

}
