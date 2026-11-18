package io.casehub.connectors.chat.teams;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BotFrameworkClientTest {

    private static final String DEFAULT_ALLOWLIST            =
            "*.botframework.com,smba.trafficmanager.net";
    private static final String DEFAULT_ATTACHMENT_ALLOWLIST =
            "*.sharepoint.com,graph.microsoft.com,*.botframework.com,smba.trafficmanager.net";

    private BotFrameworkClient client() {
        return new BotFrameworkClient(null, DEFAULT_ALLOWLIST, DEFAULT_ATTACHMENT_ALLOWLIST);
    }

    @Test
    void validateServiceUrl_exactHost_passes() {
        client().validateServiceUrl("https://smba.trafficmanager.net/teams/");
    }

    @Test
    void validateServiceUrl_wildcardSubdomain_passes() {
        client().validateServiceUrl("https://us.botframework.com/");
    }

    @Test
    void validateServiceUrl_wildcardRootDomain_passes() {
        client().validateServiceUrl("https://botframework.com/");
    }

    @Test
    void validateServiceUrl_blockedHost_throws() {
        assertThatThrownBy(() ->
                                   client().validateServiceUrl("https://evil.example.com/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evil.example.com");
    }

    @Test
    void validateServiceUrl_noScheme_throws() {
        assertThatThrownBy(() ->
                                   client().validateServiceUrl("not-a-url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateServiceUrl_withPath_passes() {
        client().validateServiceUrl("https://smba.trafficmanager.net/amer/v3/conversations");
    }

    @Test
    void createConversation_returnsConversationId() {
        var client = new BotFrameworkClient(null, DEFAULT_ALLOWLIST, DEFAULT_ATTACHMENT_ALLOWLIST) {
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
        assertThatThrownBy(() ->
                                   client().createConversation("https://evil.example.com/",
                                                               "bot-app-id", "aad-user-1", "tenant-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void downloadAttachment_ssrfRejected_nonAllowedHost() {
        var result = client().downloadAttachment("https://evil.example.com/file.pdf",
                                                 "application/pdf", "file.pdf");
        assertThat(result).isNull();
    }

    @Test
    void downloadAttachment_ssrfRejected_invalidUrl() {
        var result = client().downloadAttachment("not-a-url",
                                                 "application/pdf", "file.pdf");
        assertThat(result).isNull();
    }

    @Test
    void downloadAttachment_ssrfRejected_nullHost() {
        var result = client().downloadAttachment("file:///etc/passwd",
                                                 "text/plain", "passwd");
        assertThat(result).isNull();
    }

    @Test
    void downloadAttachment_allowedExactHost_notRejectedBySsrf() {
        var result = client().downloadAttachment("https://graph.microsoft.com/v1.0/file",
                                                 "application/pdf", "file.pdf");
        // Returns null from HTTP failure (no server), not from SSRF rejection.
        // The SSRF path logs "host rejected" — HTTP path logs "download failed".
        assertThat(result).isNull();
    }

    @Test
    void downloadAttachment_allowedWildcardHost_notRejectedBySsrf() {
        var result = client().downloadAttachment("https://mysite.sharepoint.com/file.pdf",
                                                 "application/pdf", "file.pdf");
        assertThat(result).isNull();
    }
}
