package com.Coritech.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Coritech.beans.Booking;
import com.Coritech.beans.Journey;
import com.Coritech.exception.BookingConflictException;
import com.Coritech.exception.ResourceNotFoundException;
import com.Coritech.exception.SeatsUnavailableException;
import com.Coritech.repository.JourneyRepository;
import com.Coritech.service.BookingService;


@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class BookingController {

	  @Autowired
	    private BookingService bookingService;
	    @Autowired
	    private JourneyRepository journeyRepository;

	    @PostMapping("/book")
	    public ResponseEntity<?> bookTickets(@RequestParam int userId, @RequestParam String cityName,
	            @RequestParam int noOfTickets,
	            @RequestParam(required = false) String idempotencyKey) {

	        Journey journey = journeyRepository.findByCityName(cityName);
	        if (journey == null) {
	            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Journey not found for city: " + cityName);
	        }

	        try {
	            Booking booking = bookingService.bookTickets(userId, journey.getJourneyId(), noOfTickets, idempotencyKey);
	            return ResponseEntity.ok(booking);
	        } catch (ResourceNotFoundException e) {
	            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	        } catch (SeatsUnavailableException | IllegalArgumentException e) {
	            return ResponseEntity.badRequest().body(e.getMessage());
	        } catch (BookingConflictException e) {
	            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
	        }
	    }

	    @GetMapping("/getTickets")
	    public List<Booking> getTicketsBookedByUserForEachCity(@RequestParam int userId) {
	        return bookingService.getTicketsBookedByUserForEachCity(userId);
	    }



	    @PostMapping("/cancell")
	    public ResponseEntity<?> cancelTickets(@RequestParam int bookingID) {
	        try {
	            bookingService.cancelTickets(bookingID);
	            return ResponseEntity.ok("Tickets cancelled successfully");
	        } catch (ResourceNotFoundException e) {
	            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	        } catch (BookingConflictException e) {
	            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
	        }
	    }

}
