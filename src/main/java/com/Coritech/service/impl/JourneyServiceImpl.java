package com.Coritech.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.Coritech.beans.Journey;
import com.Coritech.repository.JourneyRepository;
import com.Coritech.service.JourneyService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JourneyServiceImpl implements JourneyService {

	    private final JourneyRepository journeyRepository;

	    @Override
	    public int getTicketsRemainingForCity(String cityName) {
	        Journey journey = journeyRepository.findByCityName(cityName);
	        if (journey != null) {
	            return journey.getTicketsRemaining();
	        } else {
	            return -1; // Indicate city not found
	        }
	    }

	    @Override
	    public List<Journey> getAllJourneys() {
	        return journeyRepository.findAll();
	    }
}
