package dev.mindforge.api.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mindforge.api.config.CurrentUser;
import dev.mindforge.api.dto.request.ProfileRequest;
import dev.mindforge.api.dto.response.UserResponse;
import dev.mindforge.api.mapper.UserDtoMapper;
import dev.mindforge.application.service.AccountService;

/** The signed-in user's own profile; there is no way to reach another user's. */
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final AccountService accounts;
    private final UserDtoMapper mapper;

    public UserController(AccountService accounts, UserDtoMapper mapper) {
        this.accounts = accounts;
        this.mapper = mapper;
    }

    @Operation(summary = "Change the signed-in user's display name")
    @PatchMapping
    public UserResponse update(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ProfileRequest request) {
        return mapper.toResponse(accounts.rename(user.userId(), request.displayName()));
    }
}
