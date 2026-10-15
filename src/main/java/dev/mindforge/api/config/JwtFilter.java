package dev.mindforge.api.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.mindforge.infrastructure.security.JwtService;

/** Authenticates a request from its session cookie; a missing or invalid token leaves it anonymous. */
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (request.getCookies() != null) {
            Arrays.stream(request.getCookies())
                .filter(cookie -> JwtService.COOKIE.equals(cookie.getName()))
                .findFirst()
                .flatMap(cookie -> jwtService.verify(cookie.getValue()))
                .ifPresent(userId -> SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(new CurrentUser(userId), null, List.of())));
        }
        chain.doFilter(request, response);
    }
}
