package io.casehub.connectors.location.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.maps.GeoApiContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GoogleGeocodingTest {

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
    void geocode_success_returnsResults() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/geocode/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"OK","results":[
                                  {"formatted_address":"10 Downing Street, London",
                                   "geometry":{"location":{"lat":51.5034,"lng":-0.1276}},
                                   "place_id":"ChIJGeGQ6OcEdkgRj6NmJh4dMUs",
                                   "types":["premise"]}
                                ]}""")));

        var results = platform.geocoding("user-1").geocode("10 Downing Street");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).formattedAddress()).isEqualTo("10 Downing Street, London");
        assertThat(results.get(0).location().lat()).isEqualTo(51.5034);
    }

    @Test
    void geocode_zeroResults_returnsEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/geocode/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"ZERO_RESULTS","results":[]}""")));

        var results = platform.geocoding("user-1").geocode("zzzzz nonexistent");
        assertThat(results).isEmpty();
    }

    @Test
    void geocode_overQueryLimit_returnsEmptyFailSoft() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/geocode/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"OVER_QUERY_LIMIT",
                                 "error_message":"You have exceeded your rate-limit",
                                 "results":[]}""")));

        var results = platform.geocoding("user-1").geocode("10 Downing Street");
        assertThat(results).isEmpty();
    }
}
