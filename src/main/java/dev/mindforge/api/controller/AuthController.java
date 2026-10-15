package dev.mindforge.api.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mindforge.api.config.CurrentUser;
import dev.mindforge.api.dto.request.LoginRequest;
import dev.mindforge.api.dto.request.RegisterRequest;
import dev.mindforge.api.dto.response.AuthProvidersResponse;
import dev.mindforge.api.dto.response.UserResponse;
import dev.mindforge.api.mapper.UserDtoMapper;
import dev.mindforge.application.service.AccountService;
import dev.mindforge.domain.model.User;
import dev.mindforge.infrastructure.security.JwtService;

/** Password accounts. The session token is set as a cookie and never appears in a body. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** Deployments without OAuth set these so the app starts; such a provider cannot sign anyone in. */
    private static final List<String> PLACEHOLDER_PREFIXES = List.of("not-configured", "changeme");

    private final AccountService accounts;
    private final JwtService jwtService;
    private final UserDtoMapper mapper;
    private final ClientRegistrationRepository registrations;

    public AuthController(AccountService accounts, JwtService jwtService, UserDtoMapper mapper,
                          ClientRegistrationRepository registrations) {
        this.accounts = accounts;
        this.jwtService = jwtService;
        this.mapper = mapper;
        this.registrations = registrations;
    }

    @Operation(summary = "Register an account and sign in")
    @ApiResponse(responseCode = "201", description = "Registered; the session cookie is set")
    @ApiResponse(responseCode = "409", description = "The email already has an account")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return signedIn(HttpStatus.CREATED,
            accounts.register(request.displayName(), request.email(), request.password()));
    }

    @Operation(summary = "Sign in with email and password")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        return signedIn(HttpStatus.OK, accounts.authenticate(request.email(), request.password()));
    }

    @Operation(summary = "Sign out by clearing the session cookie")
    @ApiResponse(responseCode = "204", description = "Signed out")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, jwtService.clearedCookie().toString()).build();
    }

    @Operation(summary = "List the identity providers the sign-in page can offer")
    @GetMapping("/providers")
    public AuthProvidersResponse providers() {
        List<String> configured = new ArrayList<>();
        if (registrations instanceof Iterable<?> all) {
            for (Object entry : all) {
                if (entry instanceof ClientRegistration registration && isUsable(registration.getClientId())) {
                    configured.add(registration.getRegistrationId());
                }
            }
        }
        return new AuthProvidersResponse(configured);
    }

    private static boolean isUsable(String clientId) {
        String id = clientId == null ? "" : clientId.strip().toLowerCase(Locale.ROOT);
        return !id.isEmpty() && PLACEHOLDER_PREFIXES.stream().noneMatch(id::startsWith);
    }

    @Operation(summary = "Get the signed-in user")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CurrentUser user) {
        return mapper.toResponse(accounts.get(user.userId()));
    }

    private ResponseEntity<UserResponse> signedIn(HttpStatus status, User user) {
        return ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE, jwtService.sessionCookie(user.userId()).toString())
            .body(mapper.toResponse(user));
    }
}
