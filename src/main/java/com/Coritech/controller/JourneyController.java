package com.Coritech.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Coritech.dto.JourneyResponse;
import com.Coritech.exception.ResourceNotFoundException;
import com.Coritech.service.JourneyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Validated
@Tag(name = "Journeys", description = "Public seat-availability lookup")
public class JourneyController {

    private final JourneyService journeyService;

    @Operation(summary = "List all journeys")
    @GetMapping("/journeys")
    public List<JourneyResponse> getAllJourneys() {
        return journeyService.getAllJourneys().stream()
                .map(journey -> new JourneyResponse(journey.getJourneyId(), journey.getCityName(),
                        journey.getTicketsRemaining()))
                .toList();
    }

    @Operation(summary = "Get remaining ticket count for a city")
    @GetMapping("/journey")
    public ResponseEntity<Integer> getTicketsRemaining(@RequestParam @NotBlank String cityName) {
        int ticketsRemaining = journeyService.getTicketsRemainingForCity(cityName);
        if (ticketsRemaining < 0) {
            throw new ResourceNotFoundException("Journey details not found for " + cityName);
        }
        return ResponseEntity.ok(ticketsRemaining);
    }

}
