package com.Coritech.service.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.Coritech.beans.User;
import com.Coritech.exception.UsernameAlreadyExistsException;
import com.Coritech.repository.UserRepository;
import com.Coritech.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

    @Override
    public void register(String username, String password) {
        if (userRepository.findByUsername(username) != null) {
            throw new UsernameAlreadyExistsException("Username already exists: " + username);
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException duplicateUsername) {
            // The findByUsername check above isn't race-safe on its own;
            // the DB's unique constraint (uk_user_username) is the real
            // guard against two concurrent registrations with the same name.
            throw new UsernameAlreadyExistsException("Username already exists: " + username);
        }
    }

    @Override
    public User login(String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return user;
        } else {
            return null;
        }
    }

}
