package io.casehub.connectors.contacts.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.people.v1.PeopleService;
import io.casehub.connectors.AuthFailureReason;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.ProviderAuthException;
import io.casehub.connectors.ProviderRateLimitedException;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncTokenExpiredException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleContactReadTest {

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
    void list_success_returnsPaginatedContacts() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/people/me/connections"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"connections":[
                                  {"resourceName":"people/1",
                                   "names":[{"displayName":"Alice","givenName":"Alice","familyName":"Smith"}],
                                   "emailAddresses":[{"value":"alice@test.com"}]}
                                ],"totalPeople":1,"nextPageToken":"page2"}""")));

        var page = platform.contactRead("user-1").list(PageRequest.first(10));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).name().displayName()).isEqualTo("Alice");
        assertThat(page.nextCursor()).isEqualTo("page2");
    }

    @Test
    void list_emptyResult_returnsEmptyPage() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/people/me/connections"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"totalPeople":0}""")));

        var page = platform.contactRead("user-1").list(PageRequest.first(10));
        assertThat(page.items()).isEmpty();
    }

    @Test
    void get_success_returnsContact() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/people/1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"resourceName":"people/1",
                                 "names":[{"displayName":"Alice","givenName":"Alice","familyName":"Smith"}],
                                 "emailAddresses":[{"value":"alice@test.com"}]}""")));

        var contact = platform.contactRead("user-1").get("people/1");
        assertThat(contact.name().displayName()).isEqualTo("Alice");
    }

    @Test
    void get_404_throwsNoSuchElement() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/people/missing"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":404,"message":"Person not found",
                                 "errors":[{"reason":"notFound"}]}}""")));

        assertThatThrownBy(() -> platform.contactRead("user-1").get("people/missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void get_401_throwsProviderAuth() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/people/1"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":401,"message":"Invalid credentials",
                                 "errors":[{"reason":"authError"}]}}""")));

        assertThatThrownBy(() -> platform.contactRead("user-1").get("people/1"))
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(ex -> assertThat(((ProviderAuthException) ex).reason())
                        .isEqualTo(AuthFailureReason.TOKEN_EXPIRED));
    }

    @Test
    void get_429_throwsRateLimited() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/people/1"))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":429,"message":"Quota exceeded"}}""")));

        assertThatThrownBy(() -> platform.contactRead("user-1").get("people/1"))
                .isInstanceOf(ProviderRateLimitedException.class);
    }

    @Test
    void listSync_expiredToken_throwsSyncTokenExpired() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/people/me/connections"))
                .willReturn(aResponse().withStatus(410)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"code":410,"message":"Sync token expired",
                                 "errors":[{"reason":"syncTokenExpired"}]}}""")));

        assertThatThrownBy(() -> platform.contactRead("user-1").listSync(new SyncRequest("old", 100)))
                .isInstanceOf(SyncTokenExpiredException.class);
    }
}
