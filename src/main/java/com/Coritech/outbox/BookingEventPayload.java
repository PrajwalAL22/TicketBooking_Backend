package com.Coritech.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BookingEventPayload {

	private final Long bookingId;
	private final Long userId;
	private final Long journeyId;
	private final int noOfTicketsBooked;
	// Cumulative total cancelled on the booking after this event.
	private final int noOfTicketsCancelled;
	// Tickets cancelled by this specific event (0 for BookingConfirmed), so
	// consumers can tell a partial cancel from a full one without diffing.
	private final int ticketsCancelledInThisEvent;
	private final boolean cancelled;

}
