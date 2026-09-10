package com.Coritech.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Plain unit test, no Spring context needed — JwtService is fully
 * constructible in isolation.
 */
class JwtServiceTest {

	private final JwtService jwtService = new JwtService(
			"unit-test-only-jwt-secret-must-be-at-least-32-bytes-long", 300_000L, 86_400_000L);

	@Test
	void accessAndRefreshTokensAreDistinguishableByType() {
		String access = jwtService.generateAccessToken("alice");
		String refresh = jwtService.generateRefreshToken("alice");

		assertTrue(jwtService.isAccessToken(access));
		assertFalse(jwtService.isRefreshToken(access));

		assertTrue(jwtService.isRefreshToken(refresh));
		assertFalse(jwtService.isAccessToken(refresh));
	}

	@Test
	void extractUsernameWorksForBothTokenTypes() {
		assertEquals("bob", jwtService.extractUsername(jwtService.generateAccessToken("bob")));
		assertEquals("bob", jwtService.extractUsername(jwtService.generateRefreshToken("bob")));
	}

}
