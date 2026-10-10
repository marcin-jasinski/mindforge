package dev.mindforge.api.dto.response;

import java.util.List;

/** The identity providers a sign-in page can offer: registered with a client id that is not a placeholder. */
public record AuthProvidersResponse(List<String> providers) {}
