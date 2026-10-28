package io.casehub.connectors.document.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import io.casehub.connectors.AuthFailureReason;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.ProviderApiException;
import io.casehub.connectors.ProviderAuthException;
import io.casehub.connectors.ProviderRateLimitedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleFileOperationsTest {

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
    void download_404_throwsNoSuchElement() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files/missing"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":404,"message":"File not found",
                                 "errors":[{"reason":"notFound"}]}}""")));

        assertThatThrownBy(() -> platform.files().download("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void list_401_throwsProviderAuthTokenExpired() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":401,"message":"Invalid Credentials",
                                 "errors":[{"reason":"authError"}]}}""")));

        assertThatThrownBy(() -> platform.files().list("root", PageRequest.first(10)))
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(ex -> assertThat(((ProviderAuthException) ex).reason())
                        .isEqualTo(AuthFailureReason.TOKEN_EXPIRED));
    }

    @Test
    void get_429_throwsRateLimited() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files/file-1"))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":429,"message":"Rate Limit Exceeded"}}""")));

        assertThatThrownBy(() -> platform.files().get("file-1"))
                .isInstanceOf(ProviderRateLimitedException.class);
    }

    @Test
    void delete_403_insufficientPermissions_throwsInsufficientScope() {
        wireMock.stubFor(delete(urlPathEqualTo("/drive/v3/files/file-1"))
                .willReturn(aResponse().withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":403,"message":"Insufficient Permission",
                                 "errors":[{"reason":"insufficientPermissions"}]}}""")));

        assertThatThrownBy(() -> platform.files().delete("file-1"))
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(ex -> assertThat(((ProviderAuthException) ex).reason())
                        .isEqualTo(AuthFailureReason.INSUFFICIENT_SCOPE));
    }

    @Test
    void delete_403_domainPolicy_throwsForbidden() {
        wireMock.stubFor(delete(urlPathEqualTo("/drive/v3/files/file-1"))
                .willReturn(aResponse().withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":403,"message":"Domain Policy Block",
                                 "errors":[{"reason":"domainPolicy"}]}}""")));

        assertThatThrownBy(() -> platform.files().delete("file-1"))
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(ex -> assertThat(((ProviderAuthException) ex).reason())
                        .isEqualTo(AuthFailureReason.FORBIDDEN));
    }

    @Test
    void list_500_throwsProviderApiException() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":500,"message":"Internal Server Error"}}""")));

        assertThatThrownBy(() -> platform.files().list("root", PageRequest.first(10)))
                .isInstanceOf(ProviderApiException.class)
                .satisfies(ex -> assertThat(((ProviderApiException) ex).statusCode())
                        .isEqualTo(500));
    }

    @Test
    void get_nullOwner_handledGracefully() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files/file-1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"file-1","name":"test.txt","mimeType":"text/plain",
                                 "size":"100","modifiedTime":"2026-01-01T00:00:00.000Z"}""")));

        var metadata = platform.files().get("file-1");
        assertThat(metadata).isNotNull();
        assertThat(metadata.id()).isEqualTo("file-1");
    }
}
