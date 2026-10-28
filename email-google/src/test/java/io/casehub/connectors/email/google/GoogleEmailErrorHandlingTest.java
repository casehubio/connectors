package io.casehub.connectors.email.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import io.casehub.connectors.AuthFailureReason;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.ProviderApiException;
import io.casehub.connectors.ProviderAuthException;
import io.casehub.connectors.ProviderRateLimitedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleEmailErrorHandlingTest {

    private WireMockServer wireMock;
    private GoogleEmailPlatform platform;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        Gmail gmailService = new Gmail.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                request -> {})
                .setApplicationName("test")
                .setRootUrl("http://localhost:" + wireMock.port() + "/")
                .build();

        platform = new GoogleEmailPlatform(gmailService);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void listMailboxes_401_throwsProviderAuthTokenExpired() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/labels"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":401,"message":"Invalid Credentials",
                                 "errors":[{"reason":"authError","message":"Invalid Credentials"}]}}""")));

        assertThatThrownBy(() -> platform.listMailboxes())
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(ex -> {
                    var auth = (ProviderAuthException) ex;
                    assertThat(auth.reason()).isEqualTo(AuthFailureReason.TOKEN_EXPIRED);
                    assertThat(auth.provider()).isEqualTo("google");
                });
    }

    @Test
    void listMailboxes_403_insufficientPermissions_throwsInsufficientScope() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/labels"))
                .willReturn(aResponse().withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":403,"message":"Insufficient Permission",
                                 "errors":[{"reason":"insufficientPermissions"}]}}""")));

        assertThatThrownBy(() -> platform.listMailboxes())
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(ex -> {
                    var auth = (ProviderAuthException) ex;
                    assertThat(auth.reason()).isEqualTo(AuthFailureReason.INSUFFICIENT_SCOPE);
                });
    }

    @Test
    void listMailboxes_403_domainPolicy_throwsForbidden() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/labels"))
                .willReturn(aResponse().withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":403,"message":"Domain Policy",
                                 "errors":[{"reason":"domainPolicy"}]}}""")));

        assertThatThrownBy(() -> platform.listMailboxes())
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(ex -> {
                    var auth = (ProviderAuthException) ex;
                    assertThat(auth.reason()).isEqualTo(AuthFailureReason.FORBIDDEN);
                });
    }

    @Test
    void listMailboxes_429_throwsRateLimited() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/labels"))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":429,"message":"Rate Limit Exceeded"}}""")));

        assertThatThrownBy(() -> platform.listMailboxes())
                .isInstanceOf(ProviderRateLimitedException.class)
                .satisfies(ex -> {
                    var rl = (ProviderRateLimitedException) ex;
                    assertThat(rl.retryAfterSeconds()).isEqualTo(30);
                    assertThat(rl.provider()).isEqualTo("google");
                });
    }

    @Test
    void listMailboxes_500_throwsProviderApiException() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/labels"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":500,"message":"Internal Server Error"}}""")));

        assertThatThrownBy(() -> platform.listMailboxes())
                .isInstanceOf(ProviderApiException.class)
                .satisfies(ex -> {
                    var api = (ProviderApiException) ex;
                    assertThat(api.statusCode()).isEqualTo(500);
                    assertThat(api.provider()).isEqualTo("google");
                });
    }

    @Test
    void getMessage_401_throwsProviderAuthTokenExpired() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-1"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":401,"message":"Invalid Credentials",
                                 "errors":[{"reason":"authError"}]}}""")));

        assertThatThrownBy(() -> platform.getMessage("INBOX", "msg-1"))
                .isInstanceOf(ProviderAuthException.class);
    }

    @Test
    void getMessage_429_throwsRateLimited() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-1"))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":429,"message":"Rate Limit Exceeded"}}""")));

        assertThatThrownBy(() -> platform.getMessage("INBOX", "msg-1"))
                .isInstanceOf(ProviderRateLimitedException.class);
    }

    @Test
    void search_429_throwsRateLimited() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages"))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":429,"message":"Rate Limit Exceeded"}}""")));

        assertThatThrownBy(() -> platform.search("test query", PageRequest.first(10)))
                .isInstanceOf(ProviderRateLimitedException.class);
    }
}
