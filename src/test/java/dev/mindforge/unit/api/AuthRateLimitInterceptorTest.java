package dev.mindforge.unit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import dev.mindforge.api.config.AuthRateLimitInterceptor;
import dev.mindforge.api.config.RateLimitExceededException;

class AuthRateLimitInterceptorTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REGISTER = "/api/v1/auth/register";
    private static final String ADDRESS = "203.0.113.7";

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger interceptorLog = (Logger) LoggerFactory.getLogger(AuthRateLimitInterceptor.class);

    @BeforeEach
    void attachLogs() {
        logs.start();
        interceptorLog.setLevel(Level.INFO);
        interceptorLog.addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        interceptorLog.detachAppender(logs);
        interceptorLog.setLevel(null);
    }

    // AC5, AC1 (unit)

    @Test
    void thePostAfterTheAllowanceFromOneAddress_throwsWithTheSecondsToTheWindowEnd() throws Exception {
        AuthRateLimitInterceptor interceptor = makeInterceptor(3, Duration.ofMinutes(1));
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(makePost(LOGIN, ADDRESS), new MockHttpServletResponse(), null);
        }

        assertThatExceptionOfType(RateLimitExceededException.class)
            .isThrownBy(() -> interceptor.preHandle(makePost(LOGIN, ADDRESS), new MockHttpServletResponse(), null))
            .satisfies(e -> {
                assertThat(e.limit()).isEqualTo(3);
                assertThat(e.retryAfterSeconds()).isBetween(59L, 60L);
            });
    }

    @Test
    void aSecondAddress_isNotAffectedByTheFirstAddressesSpentAllowance() throws Exception {
        AuthRateLimitInterceptor interceptor = makeInterceptor(1, Duration.ofMinutes(1));
        interceptor.preHandle(makePost(LOGIN, ADDRESS), new MockHttpServletResponse(), null);

        assertThat(interceptor.preHandle(makePost(LOGIN, "198.51.100.9"), new MockHttpServletResponse(), null))
            .isTrue();
    }

    @Test
    void getRequests_areNotCounted() throws Exception {
        AuthRateLimitInterceptor interceptor = makeInterceptor(1, Duration.ofMinutes(1));
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/auth/me");
            get.setRemoteAddr(ADDRESS);
            interceptor.preHandle(get, new MockHttpServletResponse(), null);
        }

        assertThat(interceptor.preHandle(makePost(LOGIN, ADDRESS), new MockHttpServletResponse(), null)).isTrue();
    }

    @Test
    void afterTheWindowExpires_theAllowanceIsBack() throws Exception {
        AuthRateLimitInterceptor interceptor = makeInterceptor(1, Duration.ofMillis(200));
        interceptor.preHandle(makePost(LOGIN, ADDRESS), new MockHttpServletResponse(), null);
        assertThatExceptionOfType(RateLimitExceededException.class)
            .isThrownBy(() -> interceptor.preHandle(makePost(LOGIN, ADDRESS), new MockHttpServletResponse(), null));

        Thread.sleep(250);

        assertThat(interceptor.preHandle(makePost(LOGIN, ADDRESS), new MockHttpServletResponse(), null)).isTrue();
    }

    // AC3 (unit)

    @Test
    void loginAndRegisterFromOneAddress_drawFromTheSameBucket() throws Exception {
        AuthRateLimitInterceptor interceptor = makeInterceptor(2, Duration.ofMinutes(1));
        interceptor.preHandle(makePost(LOGIN, ADDRESS), new MockHttpServletResponse(), null);
        interceptor.preHandle(makePost(REGISTER, ADDRESS), new MockHttpServletResponse(), null);

        assertThatExceptionOfType(RateLimitExceededException.class)
            .isThrownBy(() -> interceptor.preHandle(makePost(REGISTER, ADDRESS), new MockHttpServletResponse(), null));
    }

    // AC4 (unit)

    @Test
    void anAllowedPost_carriesTheRateLimitHeaders() throws Exception {
        AuthRateLimitInterceptor interceptor = makeInterceptor(3, Duration.ofMinutes(1));
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(makePost(LOGIN, ADDRESS), response, null);

        assertThat(response.getHeader("RateLimit-Limit")).isEqualTo("3");
        assertThat(response.getHeader("RateLimit-Remaining")).isEqualTo("2");
        assertThat(response.getHeader("RateLimit-Reset")).isEqualTo("60");
    }

    // AC6

    @Test
    void theInterceptor_neverReadsTheRequestBody() throws Exception {
        AuthRateLimitInterceptor interceptor = makeInterceptor(1, Duration.ofMinutes(1));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn(ADDRESS);

        interceptor.preHandle(request, new MockHttpServletResponse(), null);
        assertThatExceptionOfType(RateLimitExceededException.class)
            .isThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), null));

        verify(request, never()).getInputStream();
        verify(request, never()).getReader();
        verify(request, never()).getParameter(anyString());
    }

    @Test
    void aRejectedPost_logsTheAddressAtInfoAndNothingFromTheBody() throws Exception {
        AuthRateLimitInterceptor interceptor = makeInterceptor(1, Duration.ofMinutes(1));
        MockHttpServletRequest first = makePost(LOGIN, ADDRESS);
        first.setContent("{\"email\":\"alice@example.com\",\"password\":\"secret-password\"}".getBytes());
        interceptor.preHandle(first, new MockHttpServletResponse(), null);
        MockHttpServletRequest second = makePost(LOGIN, ADDRESS);
        second.setContent("{\"email\":\"alice@example.com\",\"password\":\"secret-password\"}".getBytes());

        assertThatExceptionOfType(RateLimitExceededException.class)
            .isThrownBy(() -> interceptor.preHandle(second, new MockHttpServletResponse(), null));

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo("auth rate limit hit for " + ADDRESS)
                .doesNotContain("alice@example.com", "secret-password");
        });
    }

    private static AuthRateLimitInterceptor makeInterceptor(int limit, Duration window) {
        return new AuthRateLimitInterceptor(limit, window);
    }

    private static MockHttpServletRequest makePost(String path, String address) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(address);
        return request;
    }
}
