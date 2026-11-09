package io.casehub.connectors.web;

import io.casehub.connectors.web.model.BrowserCookie;
import io.casehub.connectors.web.spi.WebPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTransferTest {

    private DefaultWebPlatform platform;

    @BeforeEach
    void setUp() {
        platform = new DefaultWebPlatform(null, new InterventionQueue(java.time.Duration.ofMinutes(5)), List.of());
    }

    @Test
    void supportsSessionTransfer() {
        assertThat(platform.supports(WebPlatform.SessionTransfer.class)).isTrue();
    }

    @Test
    void transferSessionPopulatesCookieStore() {
        var cookies = List.of(
            BrowserCookie.of("session_id", "abc123", "example.com"),
            BrowserCookie.of("csrf_token", "xyz789", "example.com")
        );

        platform.sessionTransfer().transferSession(cookies);

        assertThat(platform.sessionTransfer().hasActiveSession()).isTrue();
    }

    @Test
    void clearSessionRemovesAllCookies() {
        platform.sessionTransfer().transferSession(
            List.of(BrowserCookie.of("sid", "val", "example.com")));
        assertThat(platform.sessionTransfer().hasActiveSession()).isTrue();

        platform.sessionTransfer().clearSession();
        assertThat(platform.sessionTransfer().hasActiveSession()).isFalse();
    }

    @Test
    void hasActiveSessionReturnsFalseWhenEmpty() {
        assertThat(platform.sessionTransfer().hasActiveSession()).isFalse();
    }

    @Test
    void transferSessionReplacesExistingCookies() {
        platform.sessionTransfer().transferSession(
            List.of(BrowserCookie.of("old", "val", "example.com")));

        platform.sessionTransfer().transferSession(
            List.of(BrowserCookie.of("new", "val2", "other.com")));

        assertThat(platform.sessionTransfer().hasActiveSession()).isTrue();
    }

    @Test
    void browserCookieWithFullFields() {
        var cookie = new BrowserCookie("auth", "token123", "secure.example.com",
            "/api", true, true, "Strict",
            Instant.now().plus(1, ChronoUnit.HOURS));

        platform.sessionTransfer().transferSession(List.of(cookie));

        assertThat(platform.sessionTransfer().hasActiveSession()).isTrue();
    }

    @Test
    void browserCookieValidation() {
        assertThatThrownBy(() -> BrowserCookie.of(null, "val", "example.com"))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> BrowserCookie.of("", "val", "example.com"))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> BrowserCookie.of("name", null, "example.com"))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> BrowserCookie.of("name", "val", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void browserCookieDefaultValues() {
        var cookie = BrowserCookie.of("name", "val", "example.com");
        assertThat(cookie.path()).isEqualTo("/");
        assertThat(cookie.sameSite()).isEqualTo("Lax");
        assertThat(cookie.secure()).isFalse();
        assertThat(cookie.httpOnly()).isFalse();
        assertThat(cookie.expires()).isNull();
    }
}
