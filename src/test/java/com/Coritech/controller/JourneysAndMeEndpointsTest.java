package com.Coritech.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.Coritech.beans.Journey;
import com.Coritech.beans.User;
import com.Coritech.repository.JourneyRepository;
import com.Coritech.repository.UserRepository;
import com.Coritech.security.JwtService;
import com.Coritech.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Covers the two endpoints added so the UI can (1) discover what journeys
 * exist at all, instead of only checking one already-known city name, and
 * (2) recover "who is logged in" from a stored access token alone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class JourneysAndMeEndpointsTest {

	private final MockMvc mockMvc;
	private final JourneyRepository journeyRepository;
	private final UserService userService;
	private final UserRepository userRepository;
	private final JwtService jwtService;

	@Test
	void getAllJourneysIsPublicAndListsJourneys() throws Exception {
		String cityName = "ListTestCity-" + System.nanoTime();
		Journey journey = new Journey();
		journey.setCityName(cityName);
		journey.setTicketsRemaining(3);
		journey = journeyRepository.save(journey);

		mockMvc.perform(get("/journeys"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.cityName == '" + cityName + "')].ticketsRemaining").value(3))
				.andExpect(jsonPath("$[?(@.cityName == '" + cityName + "')].journeyId").value(journey.getJourneyId().intValue()));
	}

	@Test
	void meRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void meReturnsAuthenticatedUsersProfile() throws Exception {
		String username = "me-endpoint-user-" + System.nanoTime();
		userService.register(username, "password123");
		User user = userRepository.findByUsername(username);
		String accessToken = jwtService.generateAccessToken(username);

		mockMvc.perform(get("/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value(username))
				.andExpect(jsonPath("$.userId").value(user.getUserId().intValue()));
	}

}
