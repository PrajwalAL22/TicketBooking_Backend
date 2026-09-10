package com.Coritech.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.Coritech.security.JwtService;
import com.Coritech.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Proves the security property the whole access/refresh split exists for:
 * a refresh token must never work as a Bearer access token (or shortening
 * the access token's lifetime buys nothing), and an access token must never
 * be accepted at /refresh in place of a real refresh token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class JwtRefreshFlowTest {

	private final MockMvc mockMvc;
	private final UserService userService;
	private final JwtService jwtService;

	@Test
	void refreshTokenIsRejectedWhenUsedAsBearerAccessToken() throws Exception {
		String username = "jwt-flow-user-" + System.nanoTime();
		userService.register(username, "password123");
		String refreshToken = jwtService.generateRefreshToken(username);

		mockMvc.perform(get("/getTickets").header("Authorization", "Bearer " + refreshToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void accessTokenIsRejectedByRefreshEndpoint() throws Exception {
		String username = "jwt-flow-user-" + System.nanoTime();
		userService.register(username, "password123");
		String accessToken = jwtService.generateAccessToken(username);

		mockMvc.perform(post("/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"" + accessToken + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshEndpointIssuesUsableNewAccessToken() throws Exception {
		String username = "jwt-flow-user-" + System.nanoTime();
		userService.register(username, "password123");
		String refreshToken = jwtService.generateRefreshToken(username);

		String body = mockMvc.perform(post("/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"" + refreshToken + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		String newAccessToken = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

		mockMvc.perform(get("/getTickets").header("Authorization", "Bearer " + newAccessToken))
				.andExpect(status().isOk());
	}

}
