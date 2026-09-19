package com.Coritech.service;

import java.util.List;

import com.Coritech.beans.Journey;

public interface JourneyService {

	int getTicketsRemainingForCity(String cityName);

	List<Journey> getAllJourneys();

}
