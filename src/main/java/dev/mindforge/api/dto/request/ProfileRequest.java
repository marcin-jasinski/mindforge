package dev.mindforge.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for changing the signed-in user's profile. */
public record ProfileRequest(
    @NotBlank @Size(max = 255) String displayName
) {}
