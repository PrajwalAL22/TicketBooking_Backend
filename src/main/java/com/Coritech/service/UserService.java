package com.Coritech.service;

import com.Coritech.beans.User;

public interface UserService {

	// Throws UsernameAlreadyExistsException rather than returning a sentinel,
	// consistent with how BookingService signals business-rule failures.
	void register(String username, String password);

	User login(String username, String password);

}
