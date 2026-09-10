package com.Coritech.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@Component
public class JwtService {

	private static final String HMAC_SHA_256 = "HmacSHA256";
	private static final String CLAIM_TOKEN_TYPE = "tokenType";

	// Package-private (not private) so JwtServiceTest can assert against them
	// directly instead of hardcoding the claim values a second time.
	static final String TOKEN_TYPE_ACCESS = "access";
	static final String TOKEN_TYPE_REFRESH = "refresh";

	private final SecretKey key;
	private final long accessTokenExpirationMs;
	private final long refreshTokenExpirationMs;

	public JwtService(@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
			@Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
		// Pinned to HS256 explicitly via a SecretKeySpec tagged "HmacSHA256"
		// rather than Keys.hmacShaKeyFor(bytes), which silently upgrades to
		// HS384/HS512 depending on how long the configured secret happens to
		// be. This keeps the signing algorithm deterministic regardless of
		// JWT_SECRET's length; jjwt still enforces the >= 256-bit minimum
		// (throws WeakKeyException at signing time otherwise).
		this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256);
		this.accessTokenExpirationMs = accessTokenExpirationMs;
		this.refreshTokenExpirationMs = refreshTokenExpirationMs;
	}

	// Short-lived: sent as a Bearer token on every request. A stolen access
	// token is only useful for its short remaining lifetime.
	public String generateAccessToken(String username) {
		return buildToken(username, TOKEN_TYPE_ACCESS, accessTokenExpirationMs);
	}

	// Long-lived: sent only to /refresh to obtain a new access token, never
	// used as a Bearer token on ordinary requests. JwtAuthenticationFilter
	// checks isAccessToken() precisely so a refresh token can never be used
	// to authenticate directly, even though it's a validly-signed JWT — a
	// leaked refresh token would otherwise be just as dangerous as a leaked
	// access token, defeating the point of shortening the access token's
	// lifetime in the first place.
	public String generateRefreshToken(String username) {
		return buildToken(username, TOKEN_TYPE_REFRESH, refreshTokenExpirationMs);
	}

	private String buildToken(String username, String tokenType, long expirationMs) {
		Date now = new Date();
		return Jwts.builder()
				.subject(username)
				.claim(CLAIM_TOKEN_TYPE, tokenType)
				.issuedAt(now)
				.expiration(new Date(now.getTime() + expirationMs))
				.signWith(key, Jwts.SIG.HS256)
				.compact();
	}

	public String extractUsername(String token) {
		return extractClaims(token).getSubject();
	}

	public boolean isAccessToken(String token) {
		return TOKEN_TYPE_ACCESS.equals(extractClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
	}

	public boolean isRefreshToken(String token) {
		return TOKEN_TYPE_REFRESH.equals(extractClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
	}

	public boolean isTokenValid(String token, UserDetails userDetails) {
		// Expiration is already enforced by the parser below (it throws
		// ExpiredJwtException for an expired token before this method's body
		// would ever see a Claims object to check), so only identity remains
		// to be checked here.
		return extractUsername(token).equals(userDetails.getUsername());
	}

	private Claims extractClaims(String token) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}

}
