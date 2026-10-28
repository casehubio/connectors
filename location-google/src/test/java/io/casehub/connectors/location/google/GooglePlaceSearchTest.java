package io.casehub.connectors.location.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.maps.GeoApiContext;
import io.casehub.connectors.PageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GooglePlaceSearchTest {

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
    void searchByText_success_returnsPlaces() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/place/textsearch/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"OK","results":[
                                  {"place_id":"p1","name":"Coffee Shop",
                                   "formatted_address":"123 Main St",
                                   "geometry":{"location":{"lat":51.5,"lng":-0.1}},
                                   "types":["cafe"],"rating":4.5}
                                ]}""")));

        var page = platform.placeSearch("user-1")
                .searchByText("coffee", PageRequest.first(10));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).name()).isEqualTo("Coffee Shop");
        assertThat(page.items().get(0).id()).isEqualTo("p1");
    }

    @Test
    void searchByText_emptyResults() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/place/textsearch/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"ZERO_RESULTS","results":[]}""")));

        var page = platform.placeSearch("user-1")
                .searchByText("nonexistent", PageRequest.first(10));
        assertThat(page.items()).isEmpty();
    }

    @Test
    void searchByText_overQueryLimit_returnsEmptyFailSoft() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/place/textsearch/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"OVER_QUERY_LIMIT",
                                 "error_message":"You have exceeded your rate-limit",
                                 "results":[]}""")));

        var page = platform.placeSearch("user-1")
                .searchByText("coffee", PageRequest.first(10));
        assertThat(page.items()).isEmpty();
    }

    @Test
    void searchByText_requestDenied_returnsEmptyFailSoft() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/place/textsearch/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"REQUEST_DENIED",
                                 "error_message":"API key invalid",
                                 "results":[]}""")));

        var page = platform.placeSearch("user-1")
                .searchByText("coffee", PageRequest.first(10));
        assertThat(page.items()).isEmpty();
    }
}
