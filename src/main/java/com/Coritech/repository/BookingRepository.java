package com.Coritech.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Coritech.beans.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {
	 List<Booking> findByUserUserId(Long userId);

	 Booking findByUserUserIdAndJourneyCityName(Long userId, String cityName);

	 Optional<Booking> findByIdempotencyKey(String idempotencyKey);

}
