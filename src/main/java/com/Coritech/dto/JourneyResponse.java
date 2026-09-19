package com.Coritech.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JourneyResponse {

	private final Long journeyId;
	private final String cityName;
	private final int ticketsRemaining;

}
