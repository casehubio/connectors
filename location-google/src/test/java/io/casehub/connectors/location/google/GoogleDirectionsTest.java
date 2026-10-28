package io.casehub.connectors.location.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.maps.GeoApiContext;
import io.casehub.connectors.Coordinates;
import io.casehub.connectors.ProviderRateLimitedException;
import io.casehub.connectors.location.model.TravelMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleDirectionsTest {

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
    void route_success_returnsRoute() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/directions/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"OK","routes":[{
                                  "summary":"A1",
                                  "legs":[{
                                    "start_address":"London",
                                    "end_address":"Cambridge",
                                    "start_location":{"lat":51.5,"lng":-0.1},
                                    "end_location":{"lat":52.2,"lng":0.12},
                                    "distance":{"value":100000,"text":"100 km"},
                                    "duration":{"value":3600,"text":"1 hour"}
                                  }]
                                }],"geocoded_waypoints":[]}""")));

        var route = platform.directions("user-1")
                .route(new Coordinates(51.5, -0.1),
                       new Coordinates(52.2, 0.12),
                       TravelMode.DRIVING);
        assertThat(route.summary()).isEqualTo("A1");
        assertThat(route.legs()).hasSize(1);
    }

    @Test
    void route_overQueryLimit_throwsRateLimited() {
        wireMock.stubFor(get(urlPathEqualTo("/maps/api/directions/json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":"OVER_QUERY_LIMIT",
                                 "error_message":"You have exceeded your rate-limit",
                                 "routes":[],
                                 "geocoded_waypoints":[]}""")));

        assertThatThrownBy(() -> platform.directions("user-1")
                .route(new Coordinates(51.5, -0.1),
                       new Coordinates(52.2, 0.12),
                       TravelMode.DRIVING))
                .isInstanceOf(ProviderRateLimitedException.class);
    }
}
