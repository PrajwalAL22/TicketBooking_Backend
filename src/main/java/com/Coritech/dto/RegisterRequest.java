package com.Coritech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

	@NotBlank(message = "username must not be blank")
	private String username;

	@NotBlank(message = "password must not be blank")
	@Size(min = 8, message = "password must be at least 8 characters")
	private String password;

}
