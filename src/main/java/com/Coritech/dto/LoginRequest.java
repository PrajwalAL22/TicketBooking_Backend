package com.Coritech.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

	@NotBlank(message = "username must not be blank")
	private String username;

	@NotBlank(message = "password must not be blank")
	private String password;

}
