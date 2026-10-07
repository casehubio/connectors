package io.casehub.connectors.calendar.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.EventTiming;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleCalendarPlatformTest {

    private WireMockServer wireMock;
    private GoogleCalendarPlatform platform;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        Calendar calendarService = new Calendar.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                request -> {})
                .setApplicationName("test")
                .setRootUrl("http://localhost:" + wireMock.port() + "/")
                .build();

        platform = new GoogleCalendarPlatform(calendarService);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void id_isGoogle() {
        assertThat(platform.id()).isEqualTo("google");
    }

    @Test
    void listCalendars_returnsMappedCalendars() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/users/me/calendarList"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#calendarList",
                                  "items": [
                                    {
                                      "id": "primary",
                                      "summary": "My Calendar",
                                      "description": "Main calendar",
                                      "primary": true
                                    },
                                    {
                                      "id": "work@example.com",
                                      "summary": "Work",
                                      "description": null,
                                      "primary": false
                                    }
                                  ]
                                }
                                """)));

        var calendars = platform.listCalendars();

        assertThat(calendars).hasSize(2);
        assertThat(calendars.get(0).id()).isEqualTo("primary");
        assertThat(calendars.get(0).summary()).isEqualTo("My Calendar");
        assertThat(calendars.get(0).primary()).isTrue();
        assertThat(calendars.get(1).id()).isEqualTo("work@example.com");
        assertThat(calendars.get(1).primary()).isFalse();
    }

    @Test
    void listEvents_singlePage_returnsMappedEvents() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#events",
                                  "items": [
                                    {
                                      "id": "evt-1",
                                      "summary": "Standup",
                                      "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                      "end": {"dateTime": "2026-07-26T10:30:00Z", "timeZone": "UTC"}
                                    }
                                  ]
                                }
                                """)));

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().id()).isEqualTo("evt-1");
        assertThat(events.getFirst().summary()).isEqualTo("Standup");
        assertThat(events.getFirst().timing()).isInstanceOf(EventTiming.Timed.class);
    }

    @Test
    void listEvents_pagination_collectsAllPages() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .withQueryParam("pageToken", WireMock.absent())
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#events",
                                  "items": [
                                    {
                                      "id": "evt-1",
                                      "summary": "Page 1",
                                      "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                      "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "UTC"}
                                    }
                                  ],
                                  "nextPageToken": "page2"
                                }
                                """)));

        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .withQueryParam("pageToken", WireMock.equalTo("page2"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#events",
                                  "items": [
                                    {
                                      "id": "evt-2",
                                      "summary": "Page 2",
                                      "start": {"dateTime": "2026-07-26T14:00:00Z", "timeZone": "UTC"},
                                      "end": {"dateTime": "2026-07-26T15:00:00Z", "timeZone": "UTC"}
                                    }
                                  ]
                                }
                                """)));

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(events).hasSize(2);
        assertThat(events.get(0).summary()).isEqualTo("Page 1");
        assertThat(events.get(1).summary()).isEqualTo("Page 2");
    }

    @Test
    void listEvents_midPaginationFailure_returnsPartialResults() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .withQueryParam("pageToken", WireMock.absent())
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#events",
                                  "items": [
                                    {
                                      "id": "evt-1",
                                      "summary": "Survived",
                                      "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                      "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "UTC"}
                                    }
                                  ],
                                  "nextPageToken": "page2"
                                }
                                """)));

        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .withQueryParam("pageToken", WireMock.equalTo("page2"))
                .willReturn(aResponse().withStatus(500)));

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().summary()).isEqualTo("Survived");
    }

    @Test
    void listEvents_allDayEvent_mappedCorrectly() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#events",
                                  "items": [
                                    {
                                      "id": "evt-allday",
                                      "summary": "Holiday",
                                      "start": {"date": "2026-07-27"},
                                      "end": {"date": "2026-07-28"}
                                    }
                                  ]
                                }
                                """)));

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-27T00:00:00Z"),
                Instant.parse("2026-07-28T00:00:00Z"));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().timing()).isInstanceOf(EventTiming.AllDay.class);
        var allDay = (EventTiming.AllDay) events.getFirst().timing();
        assertThat(allDay.start()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(allDay.end()).isEqualTo(LocalDate.of(2026, 7, 28));
    }

    @Test
    void getEvent_returnsMappedEvent() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "evt-1",
                                  "summary": "Found it",
                                  "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                  "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "UTC"},
                                  "attendees": [{"email": "alice@example.com"}]
                                }
                                """)));

        var event = platform.getEvent("primary", "evt-1");

        assertThat(event.summary()).isEqualTo("Found it");
        assertThat(event.attendees()).containsExactly("alice@example.com");
    }

    @Test
    void createEvent_sendsAndReturnsMappedEvent() {
        wireMock.stubFor(post(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "new-evt",
                                  "summary": "Created",
                                  "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "Europe/London"},
                                  "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "Europe/London"}
                                }
                                """)));

        var details = new EventDetails("Created", null, null,
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("Europe/London")),
                List.of());

        var event = platform.createEvent("primary", details);

        assertThat(event.id()).isEqualTo("new-evt");
        assertThat(event.summary()).isEqualTo("Created");
    }

    @Test
    void updateEvent_sendsAndReturnsMappedEvent() {
        wireMock.stubFor(put(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "evt-1",
                                  "summary": "Updated",
                                  "description": "New desc",
                                  "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                  "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "UTC"}
                                }
                                """)));

        var details = new EventDetails("Updated", "New desc", null,
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("UTC")),
                List.of());

        var event = platform.updateEvent("primary", "evt-1", details);

        assertThat(event.summary()).isEqualTo("Updated");
        assertThat(event.description()).isEqualTo("New desc");
    }

    @Test
    void deleteEvent_callsDeleteEndpoint() {
        wireMock.stubFor(delete(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-1"))
                .willReturn(aResponse().withStatus(204)));

        platform.deleteEvent("primary", "evt-1");

        wireMock.verify(1, WireMock.deleteRequestedFor(
                urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-1")));
    }

    @Test
    void requireClient_noClient_throwsIllegalState() {
        var unconfigured = new GoogleCalendarPlatform((Calendar) null);

        assertThatThrownBy(() -> unconfigured.listCalendars())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not initialised");
    }

    @Test
    void listEventsSync_initialSync_returnsEventsWithToken() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                                 .willReturn(aResponse()
                                                     .withHeader("Content-Type", "application/json")
                                                     .withBody("""
                                                               {
                                                                 "kind": "calendar#events",
                                                                 "items": [
                                                                   {
                                                                     "id": "evt-1",
                                                                     "summary": "Standup",
                                                                     "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                                                     "end": {"dateTime": "2026-07-26T10:30:00Z", "timeZone": "UTC"}
                                                                   }
                                                                 ],
                                                                 "nextSyncToken": "sync-token-1"
                                                               }
                                                               """)));

        var result = platform.listEventsSync("primary", io.casehub.connectors.SyncRequest.initial(100));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().summary()).isEqualTo("Standup");
        assertThat(result.syncToken()).isEqualTo("sync-token-1");
        assertThat(result.deletedIds()).isEmpty();
    }

    @Test
    void listEventsSync_incrementalSync_returnsCancelledAsDeletes() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                                 .withQueryParam("syncToken", WireMock.equalTo("sync-token-1"))
                                 .willReturn(aResponse()
                                                     .withHeader("Content-Type", "application/json")
                                                     .withBody("""
                                                               {
                                                                 "kind": "calendar#events",
                                                                 "items": [
                                                                   {
                                                                     "id": "evt-2",
                                                                     "summary": "New event",
                                                                     "start": {"dateTime": "2026-07-27T10:00:00Z", "timeZone": "UTC"},
                                                                     "end": {"dateTime": "2026-07-27T11:00:00Z", "timeZone": "UTC"}
                                                                   },
                                                                   {
                                                                     "id": "evt-old",
                                                                     "status": "cancelled"
                                                                   }
                                                                 ],
                                                                 "nextSyncToken": "sync-token-2"
                                                               }
                                                               """)));

        var result = platform.listEventsSync("primary",
                                             new io.casehub.connectors.SyncRequest("sync-token-1", 100));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().summary()).isEqualTo("New event");
        assertThat(result.deletedIds()).containsExactly("evt-old");
        assertThat(result.syncToken()).isEqualTo("sync-token-2");
    }

    @Test
    void listEventsSync_tokenExpired_throwsSyncTokenExpired() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                                 .withQueryParam("syncToken", WireMock.equalTo("expired-token"))
                                 .willReturn(aResponse().withStatus(410)
                                                        .withHeader("Content-Type", "application/json")
                                                        .withBody("""
                                                                  {"error": {"code": 410, "message": "Sync token expired"}}
                                                                  """)));

        assertThatThrownBy(() -> platform.listEventsSync("primary",
                                                         new io.casehub.connectors.SyncRequest("expired-token", 100)))
                .isInstanceOf(io.casehub.connectors.SyncTokenExpiredException.class);
    }

    @Test
    void listEventsSync_pagination_collectsAllPages() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                                 .withQueryParam("pageToken", WireMock.absent())
                                 .withQueryParam("syncToken", WireMock.absent())
                                 .willReturn(aResponse()
                                                     .withHeader("Content-Type", "application/json")
                                                     .withBody("""
                                                               {
                                                                 "kind": "calendar#events",
                                                                 "items": [
                                                                   {
                                                                     "id": "evt-1",
                                                                     "summary": "Page 1",
                                                                     "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                                                     "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "UTC"}
                                                                   }
                                                                 ],
                                                                 "nextPageToken": "page2"
                                                               }
                                                               """)));

        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                                 .withQueryParam("pageToken", WireMock.equalTo("page2"))
                                 .willReturn(aResponse()
                                                     .withHeader("Content-Type", "application/json")
                                                     .withBody("""
                                                               {
                                                                 "kind": "calendar#events",
                                                                 "items": [
                                                                   {
                                                                     "id": "evt-2",
                                                                     "summary": "Page 2",
                                                                     "start": {"dateTime": "2026-07-27T10:00:00Z", "timeZone": "UTC"},
                                                                     "end": {"dateTime": "2026-07-27T11:00:00Z", "timeZone": "UTC"}
                                                                   }
                                                                 ],
                                                                 "nextSyncToken": "sync-token-final"
                                                               }
                                                               """)));

        var result = platform.listEventsSync("primary", io.casehub.connectors.SyncRequest.initial(100));

        assertThat(result.items()).hasSize(2);
        assertThat(result.syncToken()).isEqualTo("sync-token-final");
    }

    @Test
    void requiresScopes_annotationPresent() {
        var annotation = GoogleCalendarPlatform.class.getAnnotation(
            io.casehub.platform.api.authn.RequiresScopes.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.provider()).isEqualTo("google");
        assertThat(annotation.scopes()).contains(
            "https://www.googleapis.com/auth/calendar.readonly",
            "https://www.googleapis.com/auth/calendar.events");
    }


    @Test
    void listEvents_recurringInstance_preservesRecurringEventId() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#events",
                                  "items": [
                                    {
                                      "id": "evt-1_20260726",
                                      "recurringEventId": "evt-1",
                                      "summary": "Weekly sync",
                                      "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                      "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "UTC"}
                                    }
                                  ]
                                }
                                """)));

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(events.getFirst().recurringEventId()).isEqualTo("evt-1");
    }

    @Test
    void getEvent_serverError_throwsRuntime() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events/bad"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> platform.getEvent("primary", "bad"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get event");
    }
}
