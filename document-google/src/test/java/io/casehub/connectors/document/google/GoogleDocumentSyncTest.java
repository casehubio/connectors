package io.casehub.connectors.document.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncTokenExpiredException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleDocumentSyncTest {

    private WireMockServer wireMock;
    private GoogleDocumentPlatform platform;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        Drive driveService = new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                request -> {})
                .setApplicationName("test")
                .setRootUrl("http://localhost:" + wireMock.port() + "/")
                .build();

        platform = new GoogleDocumentPlatform(driveService);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void initialSync_midLoopFailure_returnsPartialResultsWithNullToken() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/changes/startPageToken"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"startPageToken":"sync-token-1"}""")));

        // First page succeeds with a nextPageToken
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files"))
                .withQueryParam("pageToken", WireMock.absent())
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"files":[
                                  {"id":"f1","name":"file1.txt","mimeType":"text/plain",
                                   "modifiedTime":"2026-01-01T00:00:00.000Z"}
                                ],"nextPageToken":"page-2"}""")));

        // Second page fails with 500
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files"))
                .withQueryParam("pageToken", equalTo("page-2"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":500,"message":"Internal error"}}""")));

        var result = platform.files().listSync(SyncRequest.initial(100));
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo("f1");
        assertThat(result.syncToken()).isNull();
    }

    @Test
    void incrementalSync_expiredToken_throwsSyncTokenExpired() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/changes"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":404,"message":"Page token expired",
                                 "errors":[{"reason":"notFound"}]}}""")));

        assertThatThrownBy(() -> platform.files().listSync(new SyncRequest("old-token", 100)))
                .isInstanceOf(SyncTokenExpiredException.class);
    }
}
