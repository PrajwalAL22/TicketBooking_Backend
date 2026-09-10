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
	private final boolean cancelled;

}
