package io.casehub.connectors.travel.amadeus;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.casehub.connectors.PageRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class AmadeusRetryGuardTest {

    static WireMockServer wireMock;
    static AmadeusClient client;

    @BeforeAll
    static void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        client = new AmadeusClient("test-id", "test-secret",
                "http://localhost:" + wireMock.port(), false);
    }

    @AfterAll
    static void tearDown() {
        wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        client.clearToken();
        stubTokenEndpoint();
    }

    @Test
    void first401_triggersTokenRefreshAndRetry() {
        wireMock.stubFor(get(urlPathEqualTo("/v2/shopping/flight-offers"))
                .inScenario("retry")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(401))
                .willSetStateTo("retried"));

        wireMock.stubFor(get(urlPathEqualTo("/v2/shopping/flight-offers"))
                .inScenario("retry")
                .whenScenarioStateIs("retried")
                .willReturn(okJson(emptyFlightResponse())));

        var results = client.searchFlights("LHR", "CDG",
                LocalDate.of(2026, 12, 20), PageRequest.first(5));

        assertThat(results).isEmpty();
        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/v2/shopping/flight-offers")));
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/v1/security/oauth2/token")));
    }

    @Test
    void second401_afterRetry_returnsEmptyWithoutRecursion() {
        wireMock.stubFor(get(urlPathEqualTo("/v2/shopping/flight-offers"))
                .willReturn(aResponse().withStatus(401)));

        var results = client.searchFlights("LHR", "CDG",
                LocalDate.of(2026, 12, 20), PageRequest.first(5));

        assertThat(results).isEmpty();
        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/v2/shopping/flight-offers")));
    }

    @Test
    void nonAuthError_returnsEmptyWithoutRetry() {
        wireMock.stubFor(get(urlPathEqualTo("/v2/shopping/flight-offers"))
                .willReturn(aResponse().withStatus(500).withBody("Internal error")));

        var results = client.searchFlights("LHR", "CDG",
                LocalDate.of(2026, 12, 20), PageRequest.first(5));

        assertThat(results).isEmpty();
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/v2/shopping/flight-offers")));
    }

    private void stubTokenEndpoint() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/security/oauth2/token"))
                .willReturn(okJson("{\"access_token\":\"test-token\",\"expires_in\":3600}")));
    }

    private String emptyFlightResponse() {
        return "{\"data\":[],\"dictionaries\":{\"carriers\":{}}}";
    }
}
