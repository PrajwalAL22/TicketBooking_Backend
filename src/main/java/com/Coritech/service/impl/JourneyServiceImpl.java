package com.Coritech.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Coritech.beans.Journey;
import com.Coritech.repository.JourneyRepository;
import com.Coritech.service.JourneyService;

@Service
public class JourneyServiceImpl implements JourneyService {
	  @Autowired
	    private JourneyRepository journeyRepository;

	    @Override
	    public int getTicketsRemainingForCity(String cityName) {
	        Journey journey = journeyRepository.findByCityName(cityName);
	        if (journey != null) {
	            return journey.getTicketsRemaining();
	        } else {
	            return -1; // Indicate city not found
	        }
	    }
}
