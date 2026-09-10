package com.Coritech.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.Coritech.beans.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {
	 List<Booking> findByUserUserId(Long userId);

	 Page<Booking> findByUserUserId(Long userId, Pageable pageable);

	 Booking findByUserUserIdAndJourneyCityName(Long userId, String cityName);

	 Optional<Booking> findByIdempotencyKey(String idempotencyKey);

}
