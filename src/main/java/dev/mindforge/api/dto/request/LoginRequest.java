package dev.mindforge.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for authenticating an existing user. */
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {}
