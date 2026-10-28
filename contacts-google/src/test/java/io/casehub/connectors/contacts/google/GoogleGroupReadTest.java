package io.casehub.connectors.contacts.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.people.v1.PeopleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GoogleGroupReadTest {

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

    @Test
    void list_success_returnsGroups() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/contactGroups"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"contactGroups":[
                                  {"resourceName":"contactGroups/1","name":"Friends",
                                   "groupType":"USER_CONTACT_GROUP","memberCount":3},
                                  {"resourceName":"contactGroups/myContacts","name":"My Contacts",
                                   "groupType":"SYSTEM_CONTACT_GROUP","memberCount":10}
                                ]}""")));

        var groups = platform.groupRead("user-1").list();
        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).name()).isEqualTo("Friends");
        assertThat(groups.get(1).name()).isEqualTo("My Contacts");
    }

    @Test
    void list_emptyResult_returnsEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/contactGroups"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {}""")));

        var groups = platform.groupRead("user-1").list();
        assertThat(groups).isEmpty();
    }

    @Test
    void list_ioFailure_returnsEmptyFailSoft() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/contactGroups"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":500,"message":"Server error"}}""")));

        var groups = platform.groupRead("user-1").list();
        assertThat(groups).isEmpty();
    }
}
