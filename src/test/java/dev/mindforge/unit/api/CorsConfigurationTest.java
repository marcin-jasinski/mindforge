package dev.mindforge.unit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import dev.mindforge.api.config.SecurityConfig;

class CorsConfigurationTest {

    // AC2

    @Test
    void aBlankOriginRegistersNoConfigurationForAnyRequest() {
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource("  ");

        assertThat(source.getCorsConfiguration(makeRequest("/api/v1/auth/login"))).isNull();
        assertThat(source.getCorsConfiguration(makeRequest("/"))).isNull();
    }

    // AC3

    @Test
    void aSetOriginIsTheOnlyAllowedOriginAndCredentialsAreAllowed() {
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(" https://app.example ");

        CorsConfiguration cors = source.getCorsConfiguration(makeRequest("/api/v1/auth/login"));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("https://app.example");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.checkOrigin("https://app.example")).isEqualTo("https://app.example");
        assertThat(cors.checkOrigin("http://evil.example")).isNull();
    }

    // AC4

    @Test
    void aWildcardOriginFailsWhenTheBeanIsCreated() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> new SecurityConfig().corsConfigurationSource("*"));
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> new SecurityConfig().corsConfigurationSource("https://*.example"));
    }

    @Test
    void aListOfOriginsFailsWhenTheBeanIsCreated() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> new SecurityConfig().corsConfigurationSource("https://a.example,https://b.example"));
    }

    private static MockHttpServletRequest makeRequest(String path) {
        return new MockHttpServletRequest("OPTIONS", path);
    }
}
