package io.casehub.connectors.chat.teams;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void createConversation_returnsConversationId() {
        var client = new BotFrameworkClient(null, DEFAULT_ALLOWLIST) {
            @Override
            String postJsonWithResponse(String url, String body) {
                assertThat(url).contains("/v3/conversations");
                assertThat(body).contains("\"28:bot-app-id\"");
                assertThat(body).contains("\"aad-user-1\"");
                assertThat(body).contains("\"tenant-1\"");
                return "{\"id\":\"new-conv-123\"}";
            }
        };
        String convId = client.createConversation(
                "https://smba.trafficmanager.net/teams/",
                "bot-app-id", "aad-user-1", "tenant-1");
        assertThat(convId).isEqualTo("new-conv-123");
    }

    @Test
    void createConversation_blockedHost_throws() {
        var client = new BotFrameworkClient(null, DEFAULT_ALLOWLIST);
        assertThatThrownBy(() ->
                                   client.createConversation("https://evil.example.com/",
                                                             "bot-app-id", "aad-user-1", "tenant-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
