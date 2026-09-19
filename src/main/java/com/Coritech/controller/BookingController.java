package com.Coritech.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import com.Coritech.dto.MessageResponse;
import com.Coritech.exception.ResourceNotFoundException;
import com.Coritech.repository.JourneyRepository;
import com.Coritech.repository.UserRepository;
import com.Coritech.service.BookingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Booking, cancellation, and history for the authenticated user")
public class BookingController {

	    private final BookingService bookingService;
	    private final JourneyRepository journeyRepository;
	    private final UserRepository userRepository;

	    @Operation(summary = "Book tickets for a journey",
	            description = "Idempotent when an idempotencyKey is supplied: repeat submissions with the same key return the original booking.")
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

	    @Operation(summary = "List the authenticated user's bookings (paginated)",
	            description = "Standard Spring Data page params: page, size, sort (e.g. ?page=0&size=20).")
	    @GetMapping("/getTickets")
	    public Page<BookingResponse> getTicketsBookedByUserForEachCity(Authentication authentication,
	            Pageable pageable) {
	        return bookingService.getTicketsBookedByUserForEachCity(resolveUserId(authentication), pageable)
	                .map(this::toResponse);
	    }

	    @Operation(summary = "Cancel a booking",
	            description = "Only the booking's owner may cancel it; cancelling an already-cancelled booking is a no-op.")
	    @PostMapping("/cancell")
	    public ResponseEntity<MessageResponse> cancelTickets(Authentication authentication,
	            @Valid @RequestBody CancelRequest request) {
	        bookingService.cancelTickets(resolveUserId(authentication), request.getBookingId());
	        return ResponseEntity.ok(new MessageResponse("Tickets cancelled successfully"));
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
