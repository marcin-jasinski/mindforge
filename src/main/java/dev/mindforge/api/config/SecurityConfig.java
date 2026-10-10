package dev.mindforge.api.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import dev.mindforge.application.service.AccountService;
import dev.mindforge.infrastructure.security.JwtService;

/**
 * The filter chain: every {@code /api/**} request except register, login and logout needs the session cookie, and
 * gets 401 rather than a redirect without it. CSRF is off for the API, whose cookie is {@code SameSite=Lax}; OAuth
 * sign-in keeps Spring Security's state validation. Only the health probe is public under
 * {@code /actuator}; the SPA and static files are public.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, AccountService accountService)
        throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            // everything the SPA loads is its own: no inline script (so critical-CSS inlining is off in the build), no
            // external font or API origin; Angular Material sets inline styles, hence style-src 'unsafe-inline'
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                    + "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; "
                    + "form-action 'self'; frame-ancestors 'none'")))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/logout",
                    "/api/v1/auth/providers").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/**", "/actuator/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
            .oauth2Login(login -> login
                // the SPA owns /login; without this, Spring answers a direct visit with its generated sign-in page
                .loginPage("/login")
                .successHandler(new OAuthLoginSuccessHandler(accountService, jwtService)))
            .logout(logout -> logout.disable())
            .addFilterBefore(new JwtFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
