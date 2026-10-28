package io.casehub.connectors.document.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import io.casehub.connectors.ProviderAuthException;
import io.casehub.connectors.ProviderRateLimitedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleFolderOperationsTest {

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
    void move_404_throwsNoSuchElement() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files/file-1"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":404,"message":"File not found",
                                 "errors":[{"reason":"notFound"}]}}""")));

        assertThatThrownBy(() -> platform.folders().move("file-1", "folder-2"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void create_429_throwsRateLimited() {
        wireMock.stubFor(post(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":429,"message":"Rate Limit Exceeded"}}""")));

        assertThatThrownBy(() -> platform.folders().create("root", "New Folder"))
                .isInstanceOf(ProviderRateLimitedException.class);
    }

    @Test
    void list_401_throwsProviderAuth() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":401,"message":"Invalid Credentials",
                                 "errors":[{"reason":"authError"}]}}""")));

        assertThatThrownBy(() -> platform.folders().list("root"))
                .isInstanceOf(ProviderAuthException.class);
    }
}
