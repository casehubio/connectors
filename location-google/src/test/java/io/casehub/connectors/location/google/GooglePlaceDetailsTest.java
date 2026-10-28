package io.casehub.connectors.location.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.maps.GeoApiContext;
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

class GooglePlaceDetailsTest {

    private WireMockServer wireMock;
    private GoogleLocationPlatform platform;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        GeoApiContext geoContext = new GeoApiContext.Builder()
                .apiKey("AIzaTestKey0000000000000000000")
                .baseUrlOverride("http://localhost:" + wireMock.port())
                .disableRetries()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .queryRateLimit(1000)
                .build();

        platform = new GoogleLocationPlatform(geoContext);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void get_success_returnsPlaceDetail() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/place/details/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"OK","result":{
                                  "place_id":"p1","name":"Coffee Shop",
                                  "formatted_address":"123 Main St",
                                  "geometry":{"location":{"lat":51.5,"lng":-0.1}},
                                  "formatted_phone_number":"+44 20 1234 5678",
                                  "rating":4.5,
                                  "price_level":2}}""")));

        var detail = platform.placeDetails("user-1").get("p1");
        assertThat(detail.name()).isEqualTo("Coffee Shop");
        assertThat(detail.id()).isEqualTo("p1");
    }

    @Test
    void get_requestDenied_throwsProviderAuth() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/place/details/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"REQUEST_DENIED",
                                 "error_message":"API key invalid"}""")));

        assertThatThrownBy(() -> platform.placeDetails("user-1").get("p1"))
                .isInstanceOf(ProviderAuthException.class);
    }

    @Test
    void get_overQueryLimit_throwsRateLimited() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/place/details/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"OVER_QUERY_LIMIT",
                                 "error_message":"You have exceeded your rate-limit"}""")));

        assertThatThrownBy(() -> platform.placeDetails("user-1").get("p1"))
                .isInstanceOf(ProviderRateLimitedException.class);
    }

    @Test
    void get_unknownError_throwsProviderApiException() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/place/details/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"UNKNOWN_ERROR",
                                 "error_message":"Something went wrong"}""")));

        assertThatThrownBy(() -> platform.placeDetails("user-1").get("p1"))
                .isInstanceOf(ProviderApiException.class);
    }
}
