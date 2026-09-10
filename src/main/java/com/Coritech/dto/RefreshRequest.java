package com.Coritech.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshRequest {

	@NotBlank(message = "refreshToken must not be blank")
	private String refreshToken;

}
