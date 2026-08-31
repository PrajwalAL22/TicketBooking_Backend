package com.Coritech.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Coritech.beans.Journey;

public interface JourneyRepository extends JpaRepository<Journey, Long> {
	Journey findByCityName(String cityName);
}
