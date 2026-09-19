package com.Coritech.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.Coritech.beans.User;
import com.Coritech.dto.AccessTokenResponse;
import com.Coritech.dto.AuthResponse;
import com.Coritech.dto.LoginRequest;
import com.Coritech.dto.MessageResponse;
import com.Coritech.dto.RefreshRequest;
import com.Coritech.dto.RegisterRequest;
import com.Coritech.dto.UserProfileResponse;
import com.Coritech.exception.InvalidCredentialsException;
import com.Coritech.exception.InvalidTokenException;
import com.Coritech.exception.ResourceNotFoundException;
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
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(new MessageResponse("Registration successful"));
    }

    @Operation(summary = "Log in",
            description = "Returns a short-lived access token (send as `Authorization: Bearer <accessToken>`) "
                    + "and a longer-lived refresh token (exchange at /refresh for a new access token once it expires).")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.login(request.getUsername(), request.getPassword());
        if (user == null) {
            throw new InvalidCredentialsException("Invalid username or password");
        }
        String accessToken = jwtService.generateAccessToken(user.getUsername());
        String refreshToken = jwtService.generateRefreshToken(user.getUsername());
        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, user.getUsername(), user.getUserId()));
    }

    @Operation(summary = "Exchange a refresh token for a new access token",
            description = "The refresh token itself is not rotated/reissued — it keeps its original 24h expiry, "
                    + "after which the client must log in again.")
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        String username;
        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                throw new InvalidTokenException("Not a refresh token");
            }
            username = jwtService.extractUsername(refreshToken);
        } catch (JwtException | IllegalArgumentException malformedOrExpired) {
            // Expired/malformed/tampered token: same outward response as any
            // other invalid refresh token, not a 500.
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        if (userRepository.findByUsername(username) == null) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        return ResponseEntity.ok(new AccessTokenResponse(jwtService.generateAccessToken(username)));
    }

    @Operation(summary = "Log out", description = "Stateless JWT: nothing is invalidated server-side, the client just discards its tokens.")
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout() {
        // Stateless JWT: there's no server-side session to invalidate, so this
        // is just a client-facing confirmation — the client is responsible for
        // discarding both tokens. (Requires a valid access token to reach at
        // all, since it isn't in the permitAll list in SecurityConfig.)
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    @Operation(summary = "Get the authenticated user's profile",
            description = "Lets the client recover \"who is logged in\" from a stored access token alone, "
                    + "without having to separately persist the username/userId returned at login.")
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName());
        if (user == null) {
            throw new ResourceNotFoundException("Authenticated user not found: " + authentication.getName());
        }
        return ResponseEntity.ok(new UserProfileResponse(user.getUserId(), user.getUsername()));
    }

}
