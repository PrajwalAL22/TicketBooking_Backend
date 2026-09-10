package com.Coritech.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.Coritech.beans.User;
import com.Coritech.dto.AccessTokenResponse;
import com.Coritech.dto.AuthResponse;
import com.Coritech.dto.LoginRequest;
import com.Coritech.dto.RefreshRequest;
import com.Coritech.dto.RegisterRequest;
import com.Coritech.repository.UserRepository;
import com.Coritech.security.JwtService;
import com.Coritech.service.UserService;

import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Users", description = "Registration and JWT-based authentication")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userService.register(request.getUsername(), request.getPassword())) {
            return ResponseEntity.ok("Registration successful");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username already exists");
        }
    }

    @Operation(summary = "Log in",
            description = "Returns a short-lived access token (send as `Authorization: Bearer <accessToken>`) "
                    + "and a longer-lived refresh token (exchange at /refresh for a new access token once it expires).")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.login(request.getUsername(), request.getPassword());
        if (user != null) {
            String accessToken = jwtService.generateAccessToken(user.getUsername());
            String refreshToken = jwtService.generateRefreshToken(user.getUsername());
            return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, user.getUsername(), user.getUserId()));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }
    }

    @Operation(summary = "Exchange a refresh token for a new access token",
            description = "The refresh token itself is not rotated/reissued — it keeps its original 24h expiry, "
                    + "after which the client must log in again.")
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        String username;
        try {
            String refreshToken = request.getRefreshToken();
            if (!jwtService.isRefreshToken(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not a refresh token");
            }
            username = jwtService.extractUsername(refreshToken);
        } catch (JwtException | IllegalArgumentException invalidToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired refresh token");
        }

        if (userRepository.findByUsername(username) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired refresh token");
        }

        return ResponseEntity.ok(new AccessTokenResponse(jwtService.generateAccessToken(username)));
    }

    @Operation(summary = "Log out", description = "Stateless JWT: nothing is invalidated server-side, the client just discards its tokens.")
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // Stateless JWT: there's no server-side session to invalidate, so this
        // is just a client-facing confirmation — the client is responsible for
        // discarding both tokens. (Requires a valid access token to reach at
        // all, since it isn't in the permitAll list in SecurityConfig.)
        return ResponseEntity.ok("Logged out successfully");
    }

}
