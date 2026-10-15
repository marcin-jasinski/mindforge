package dev.mindforge.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An explicit edit of the wiki from chat; {@code quotedAnswer} is the answer a "save that" keeps. */
public record EditRequest(@NotBlank @Size(max = 4000) String instruction, @Size(max = 20000) String quotedAnswer) {}
