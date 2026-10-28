package io.casehub.connectors.document.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.ProviderAuthException;
import io.casehub.connectors.ProviderRateLimitedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleSearchSharingTest {

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
    void search_429_throwsRateLimited() {
        wireMock.stubFor(get(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":429,"message":"Rate Limit Exceeded"}}""")));

        assertThatThrownBy(() -> platform.search().search("test", PageRequest.first(10)))
                .isInstanceOf(ProviderRateLimitedException.class);
    }

    @Test
    void getShareLink_401_throwsProviderAuth() {
        wireMock.stubFor(post(urlPathEqualTo("/drive/v3/files/file-1/permissions"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":401,"message":"Invalid Credentials",
                                 "errors":[{"reason":"authError"}]}}""")));

        assertThatThrownBy(() -> platform.sharing().getShareLink("file-1"))
                .isInstanceOf(ProviderAuthException.class);
    }
}
