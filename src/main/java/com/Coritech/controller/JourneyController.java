package com.Coritech.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @Operation(summary = "Get remaining ticket count for a city")
    @GetMapping("/journey")
    public ResponseEntity<?> getTicketsRemaining(@RequestParam @NotBlank String cityName) {
        int ticketsRemaining = journeyService.getTicketsRemainingForCity(cityName);
        if (ticketsRemaining >= 0) {
            return ResponseEntity.ok(ticketsRemaining);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Journey details not found for " + cityName);
        }
    }

}
