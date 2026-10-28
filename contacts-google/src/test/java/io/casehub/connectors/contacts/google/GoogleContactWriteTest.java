package io.casehub.connectors.contacts.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.people.v1.PeopleService;
import io.casehub.connectors.ProviderAuthException;
import io.casehub.connectors.contacts.model.Contact;
import io.casehub.connectors.contacts.model.ContactName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleContactWriteTest {

    private WireMockServer wireMock;
    private GoogleContactsPlatform platform;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        PeopleService peopleService = new PeopleService.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                request -> {})
                .setApplicationName("test")
                .setRootUrl("http://localhost:" + wireMock.port() + "/")
                .build();

        platform = new GoogleContactsPlatform(peopleService);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    private Contact testContact() {
        return new Contact(null,
                new ContactName("Test User", "Test", "User"),
                List.of(), List.of(), List.of(),
                null, null, null, null, Map.of());
    }

    @Test
    void create_401_throwsProviderAuth() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/people:createContact"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":401,"message":"Invalid credentials",
                                 "errors":[{"reason":"authError"}]}}""")));

        assertThatThrownBy(() -> platform.contactWrite("user-1").create(testContact()))
                .isInstanceOf(ProviderAuthException.class);
    }

    @Test
    void delete_404_throwsNoSuchElement() {
        wireMock.stubFor(delete(urlPathEqualTo("/v1/people/missing:deleteContact"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":404,"message":"Person not found",
                                 "errors":[{"reason":"notFound"}]}}""")));

        assertThatThrownBy(() -> platform.contactWrite("user-1").delete("people/missing"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
