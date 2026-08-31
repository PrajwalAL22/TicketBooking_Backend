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

}
