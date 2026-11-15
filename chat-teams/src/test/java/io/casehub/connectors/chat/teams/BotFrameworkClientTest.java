package io.casehub.connectors.chat.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BotFrameworkClientTest {

    private static final String DEFAULT_ALLOWLIST =
            "*.botframework.com,smba.trafficmanager.net";

    @Test
    void validateServiceUrl_exactHost_passes() {
        var client = new BotFrameworkClient(null, DEFAULT_ALLOWLIST);
        client.validateServiceUrl("https://smba.trafficmanager.net/teams/");
    }

    @Test
    void validateServiceUrl_wildcardSubdomain_passes() {
        var client = new BotFrameworkClient(null, DEFAULT_ALLOWLIST);
        client.validateServiceUrl("https://us.botframework.com/");
    }

    @Test
    void validateServiceUrl_wildcardRootDomain_passes() {
        var client = new BotFrameworkClient(null, DEFAULT_ALLOWLIST);
        client.validateServiceUrl("https://botframework.com/");
    }

    @Test
    void validateServiceUrl_blockedHost_throws() {
        var client = new BotFrameworkClient(null, DEFAULT_ALLOWLIST);
        assertThatThrownBy(() ->
                client.validateServiceUrl("https://evil.example.com/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evil.example.com");
    }

    @Test
    void validateServiceUrl_noScheme_throws() {
        var client = new BotFrameworkClient(null, DEFAULT_ALLOWLIST);
        assertThatThrownBy(() ->
                client.validateServiceUrl("not-a-url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateServiceUrl_withPath_passes() {
        var client = new BotFrameworkClient(null, DEFAULT_ALLOWLIST);
        client.validateServiceUrl("https://smba.trafficmanager.net/amer/v3/conversations");
    }
}
