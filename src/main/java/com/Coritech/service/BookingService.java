package com.Coritech.service;

import java.util.List;

import com.Coritech.beans.Booking;

public interface BookingService {

	Booking bookTickets(int userId, int journeyId, int noOfTickets, String idempotencyKey);

	void cancelTickets(int bookingId);

	// Method to get List of tickets booked by User
	List<Booking> getTicketsBookedByUserForEachCity(int userId);

}
