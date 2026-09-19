package com.Coritech.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Plain success-confirmation body (e.g. "Registration successful"), so
 * simple message responses are valid JSON like everything else in this API
 * instead of a bare text/plain string.
 */
@Getter
@AllArgsConstructor
public class MessageResponse {

	private final String message;

}
