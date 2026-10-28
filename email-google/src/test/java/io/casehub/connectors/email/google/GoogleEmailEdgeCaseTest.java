package io.casehub.connectors.email.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import io.casehub.connectors.PageRequest;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GoogleEmailEdgeCaseTest {

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
    void getMessage_nullHeaders_handledGracefully() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"msg-1","threadId":"t1","labelIds":["INBOX"],
                                 "internalDate":"1704067200000",
                                 "payload":{"mimeType":"text/plain",
                                            "body":{"data":"SGVsbG8="}}}""")));

        var message = platform.getMessage("INBOX", "msg-1");
        assertThat(message).isNotNull();
        assertThat(message.id()).isEqualTo("msg-1");
    }

    @Test
    void getMessage_emptyPayloadBody_handledGracefully() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-2"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"msg-2","threadId":"t1","labelIds":["INBOX"],
                                 "internalDate":"1704067200000",
                                 "payload":{"mimeType":"text/plain","headers":[
                                   {"name":"Subject","value":"Test"},
                                   {"name":"From","value":"sender@test.com"},
                                   {"name":"Date","value":"Mon, 1 Jan 2026 00:00:00 +0000"}
                                 ],"body":{}}}""")));

        var message = platform.getMessage("INBOX", "msg-2");
        assertThat(message).isNotNull();
    }

    @Test
    void listMessages_individualFetchFailure_skipsAndContinues() {
        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"messages":[{"id":"msg-ok"},{"id":"msg-fail"}]}""")));

        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-ok"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"msg-ok","threadId":"t1","labelIds":["INBOX"],
                                 "snippet":"Hello","internalDate":"1704067200000",
                                 "payload":{"headers":[
                                   {"name":"Subject","value":"OK"},
                                   {"name":"From","value":"a@test.com"}]}}""")));

        wireMock.stubFor(get(urlPathEqualTo("/gmail/v1/users/me/messages/msg-fail"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":500,"message":"Server error"}}""")));

        var page = platform.listMessages("INBOX",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                PageRequest.first(10));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo("msg-ok");
    }
}
