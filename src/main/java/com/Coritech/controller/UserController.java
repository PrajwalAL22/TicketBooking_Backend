package com.Coritech.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.Coritech.beans.User;
import com.Coritech.dto.AuthResponse;
import com.Coritech.dto.LoginRequest;
import com.Coritech.dto.RegisterRequest;
import com.Coritech.security.JwtService;
import com.Coritech.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userService.register(request.getUsername(), request.getPassword())) {
            return ResponseEntity.ok("Registration successful");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username already exists");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.login(request.getUsername(), request.getPassword());
        if (user != null) {
            String token = jwtService.generateToken(user.getUsername());
            return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getUserId()));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // Stateless JWT: there's no server-side session to invalidate, so this
        // is just a client-facing confirmation — the client is responsible for
        // discarding the token. (Requires a valid token to reach at all, since
        // it isn't in the permitAll list in SecurityConfig.)
        return ResponseEntity.ok("Logged out successfully");
    }

}
