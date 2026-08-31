package com.Coritech.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.Coritech.beans.Booking;
import com.Coritech.beans.Journey;
import com.Coritech.beans.User;
import com.Coritech.dto.BookingRequest;
import com.Coritech.dto.BookingResponse;
import com.Coritech.dto.CancelRequest;
import com.Coritech.exception.ResourceNotFoundException;
import com.Coritech.repository.JourneyRepository;
import com.Coritech.repository.UserRepository;
import com.Coritech.service.BookingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BookingController {

	    private final BookingService bookingService;
	    private final JourneyRepository journeyRepository;
	    private final UserRepository userRepository;

	    @PostMapping("/book")
	    public ResponseEntity<BookingResponse> bookTickets(Authentication authentication,
	            @Valid @RequestBody BookingRequest request) {

	        Journey journey = journeyRepository.findByCityName(request.getCityName());
	        if (journey == null) {
	            throw new ResourceNotFoundException("Journey not found for city: " + request.getCityName());
	        }

	        Long userId = resolveUserId(authentication);
	        Booking booking = bookingService.bookTickets(userId, journey.getJourneyId(), request.getNoOfTickets(),
	                request.getIdempotencyKey());
	        return ResponseEntity.ok(toResponse(booking));
	    }

	    @GetMapping("/getTickets")
	    public List<BookingResponse> getTicketsBookedByUserForEachCity(Authentication authentication) {
	        return bookingService.getTicketsBookedByUserForEachCity(resolveUserId(authentication)).stream()
	                .map(this::toResponse)
	                .toList();
	    }

	    @PostMapping("/cancell")
	    public ResponseEntity<String> cancelTickets(Authentication authentication,
	            @Valid @RequestBody CancelRequest request) {
	        bookingService.cancelTickets(resolveUserId(authentication), request.getBookingId());
	        return ResponseEntity.ok("Tickets cancelled successfully");
	    }

	    // Authenticated username (from the validated JWT) -> userId, so
	    // booking/cancellation always act on the caller's own account instead
	    // of a client-supplied userId.
	    private Long resolveUserId(Authentication authentication) {
	        User user = userRepository.findByUsername(authentication.getName());
	        if (user == null) {
	            throw new ResourceNotFoundException("Authenticated user not found: " + authentication.getName());
	        }
	        return user.getUserId();
	    }

	    private BookingResponse toResponse(Booking booking) {
	        return BookingResponse.builder()
	                .bookingId(booking.getBookingId())
	                .journeyId(booking.getJourney().getJourneyId())
	                .cityName(booking.getJourney().getCityName())
	                .noOfTicketsBooked(booking.getNoOfTicketsBooked())
	                .cancelled(booking.isCancelled())
	                .idempotencyKey(booking.getIdempotencyKey())
	                .build();
	    }

}
