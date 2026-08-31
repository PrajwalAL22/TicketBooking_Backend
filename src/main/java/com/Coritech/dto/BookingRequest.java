package com.Coritech.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookingRequest {

	@NotBlank(message = "cityName must not be blank")
	private String cityName;

	@Min(value = 1, message = "noOfTickets must be at least 1")
	private int noOfTickets;

	// Matches Booking.idempotencyKey's column length.
	@Size(max = 100, message = "idempotencyKey must be at most 100 characters")
	private String idempotencyKey;

}
