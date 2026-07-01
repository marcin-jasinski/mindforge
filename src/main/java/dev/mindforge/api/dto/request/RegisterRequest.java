package dev.mindforge.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for registering a new user account. */
public record RegisterRequest(
    @NotBlank String displayName,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password
) {}
