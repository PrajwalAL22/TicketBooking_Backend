package com.Coritech.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Coritech.beans.User;

public interface UserRepository extends JpaRepository<User, Long> {
	User findByUsername(String username);
}
