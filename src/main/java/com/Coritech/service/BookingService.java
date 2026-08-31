package com.Coritech.service;

import java.util.List;

import com.Coritech.beans.Booking;

public interface BookingService {

	Booking bookTickets(Long userId, Long journeyId, int noOfTickets, String idempotencyKey);

	void cancelTickets(Long userId, Long bookingId);

	// Method to get List of tickets booked by User
	List<Booking> getTicketsBookedByUserForEachCity(Long userId);

}
