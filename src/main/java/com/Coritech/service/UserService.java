package com.Coritech.service;

import com.Coritech.beans.User;

public interface UserService {

	boolean register(String username, String password);

	User login(String username, String password);

}
