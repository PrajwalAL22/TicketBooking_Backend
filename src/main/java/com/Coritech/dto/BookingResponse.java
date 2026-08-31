package com.Coritech.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BookingResponse {

	private Long bookingId;
	private Long journeyId;
	private String cityName;
	private int noOfTicketsBooked;
	private boolean cancelled;
	private String idempotencyKey;

}
