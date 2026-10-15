package com.Coritech.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelRequest {

	@NotNull(message = "bookingId must not be null")
	@Positive(message = "bookingId must be positive")
	private Long bookingId;

	// Optional: omit to cancel every still-active ticket on the booking.
	@Positive(message = "noOfTickets must be positive")
	private Integer noOfTickets;

}
