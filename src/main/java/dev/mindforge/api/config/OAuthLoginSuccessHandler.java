package dev.mindforge.api.config;

import java.io.IOException;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import dev.mindforge.application.service.AccountService;
import dev.mindforge.domain.model.User;
import dev.mindforge.infrastructure.security.JwtService;

/**
 * Finishes a Google or GitHub sign-in (Spring Security validates the OAuth {@code state}): signs the provider's email
 * in, sets the session cookie and returns to the SPA. A provider that shares no email, or says it is unverified, is
 * refused.
 */
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AccountService accounts;
    private final JwtService jwtService;

    public OAuthLoginSuccessHandler(AccountService accounts, JwtService jwtService) {
        this.accounts = accounts;
        this.jwtService = jwtService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User provider = (OAuth2User) authentication.getPrincipal();
        String email = provider.getAttribute("email");
        // ponytail: GitHub shares only a public email; fetching /user/emails is the upgrade when users hit this
        if (email == null || email.isBlank() || Boolean.FALSE.equals(provider.getAttribute("email_verified"))) {
            response.sendRedirect("/login?error=email");
            return;
        }
        User user = accounts.signInWithProvider(email, first(provider, "name", "login"),
            first(provider, "picture", "avatar_url"));
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.sessionCookie(user.userId()).toString());
        response.sendRedirect("/dashboard");
    }

    private static String first(OAuth2User provider, String... attributes) {
        return Stream.of(attributes).map(provider::<Object>getAttribute).filter(value -> value != null)
            .map(String::valueOf).findFirst().orElse(null);
    }
}
