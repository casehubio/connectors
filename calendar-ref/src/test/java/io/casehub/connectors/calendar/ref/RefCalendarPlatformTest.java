package io.casehub.connectors.calendar.ref;

import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.EventTiming;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefCalendarPlatformTest {

    private RefCalendarPlatform platform;
    private InMemoryCalendarBackend backend;

    @BeforeEach
    void setUp() {
        backend = new InMemoryCalendarBackend();
        platform = new RefCalendarPlatform(backend);
    }

    @Test
    void id_isRef() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void listCalendars_returnsDefault() {
        assertThat(platform.listCalendars()).isNotEmpty();
        assertThat(platform.listCalendars().getFirst().primary()).isTrue();
    }

    @Test
    void createEvent_timedEvent_returnsWithId() {
        var details = new EventDetails("Meeting", "Standup", "Room 1",
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("Europe/London")),
                List.of("alice@example.com"));

        var event = platform.createEvent("primary", details);

        assertThat(event.id()).isNotNull();
        assertThat(event.calendarId()).isEqualTo("primary");
        assertThat(event.summary()).isEqualTo("Meeting");
        assertThat(event.attendees()).containsExactly("alice@example.com");
        assertThat(event.timing()).isInstanceOf(EventTiming.Timed.class);
    }

    @Test
    void createEvent_allDayEvent() {
        var details = new EventDetails("Holiday", null, null,
                new EventTiming.AllDay(
                        LocalDate.of(2026, 7, 27),
                        LocalDate.of(2026, 7, 28)),
                List.of());

        var event = platform.createEvent("primary", details);

        assertThat(event.timing()).isInstanceOf(EventTiming.AllDay.class);
        var allDay = (EventTiming.AllDay) event.timing();
        assertThat(allDay.start()).isEqualTo(LocalDate.of(2026, 7, 27));
    }

    @Test
    void listEvents_returnsCreatedEvents() {
        var details = new EventDetails("Test", null, null,
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("UTC")),
                List.of());
        platform.createEvent("primary", details);

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().summary()).isEqualTo("Test");
    }

    @Test
    void listEvents_filtersOutsideRange() {
        var details = new EventDetails("Outside", null, null,
                new EventTiming.Timed(
                        Instant.parse("2026-07-28T10:00:00Z"),
                        Instant.parse("2026-07-28T11:00:00Z"),
                        ZoneId.of("UTC")),
                List.of());
        platform.createEvent("primary", details);

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(events).isEmpty();
    }

    @Test
    void listEvents_allDayEvent_inRange() {
        var details = new EventDetails("Holiday", null, null,
                new EventTiming.AllDay(
                        LocalDate.of(2026, 7, 26),
                        LocalDate.of(2026, 7, 27)),
                List.of());
        platform.createEvent("primary", details);

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(events).hasSize(1);
    }

    @Test
    void getEvent_existing_returnsEvent() {
        var details = new EventDetails("Find me", null, null,
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("UTC")),
                List.of());
        var created = platform.createEvent("primary", details);

        var found = platform.getEvent("primary", created.id());
        assertThat(found.summary()).isEqualTo("Find me");
    }

    @Test
    void getEvent_nonExistent_throws() {
        assertThatThrownBy(() -> platform.getEvent("primary", "no-such-id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateEvent_replacesSummary() {
        var details = new EventDetails("Original", null, null,
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("UTC")),
                List.of());
        var created = platform.createEvent("primary", details);

        var updated = platform.updateEvent("primary", created.id(),
                new EventDetails("Updated", "new desc", "Room 2",
                        details.timing(), List.of("bob@example.com")));

        assertThat(updated.summary()).isEqualTo("Updated");
        assertThat(updated.description()).isEqualTo("new desc");
        assertThat(updated.attendees()).containsExactly("bob@example.com");
    }

    @Test
    void deleteEvent_removesEvent() {
        var details = new EventDetails("Delete me", null, null,
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("UTC")),
                List.of());
        var created = platform.createEvent("primary", details);

        platform.deleteEvent("primary", created.id());

        assertThatThrownBy(() -> platform.getEvent("primary", created.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listEvents_differentCalendars_isolated() {
        var details = new EventDetails("Cal A event", null, null,
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("UTC")),
                List.of());
        platform.createEvent("cal-a", details);

        var events = platform.listEvents("cal-b",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));
        assertThat(events).isEmpty();
    }

    @Test
    void listEventsSync_initialSync_returnsAllEventsWithToken() {
        var details = new EventDetails("Sync me", null, null,
                                       new EventTiming.Timed(
                                               Instant.parse("2026-07-26T10:00:00Z"),
                                               Instant.parse("2026-07-26T11:00:00Z"),
                                               ZoneId.of("UTC")),
                                       List.of());
        platform.createEvent("primary", details);

        var result = platform.listEventsSync("primary", SyncRequest.initial(100));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().summary()).isEqualTo("Sync me");
        assertThat(result.syncToken()).isNotNull();
        assertThat(result.deletedIds()).isEmpty();
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void listEventsSync_incrementalSync_detectsNewEvents() {
        platform.createEvent("primary", new EventDetails("Initial", null, null,
                                                         new EventTiming.Timed(
                                                                 Instant.parse("2026-07-26T10:00:00Z"),
                                                                 Instant.parse("2026-07-26T11:00:00Z"),
                                                                 ZoneId.of("UTC")),
                                                         List.of()));

        var fullSync = platform.listEventsSync("primary", SyncRequest.initial(100));
        var token    = fullSync.syncToken();

        platform.createEvent("primary", new EventDetails("New event", null, null,
                                                         new EventTiming.Timed(
                                                                 Instant.parse("2026-07-27T10:00:00Z"),
                                                                 Instant.parse("2026-07-27T11:00:00Z"),
                                                                 ZoneId.of("UTC")),
                                                         List.of()));

        var incrementalSync = platform.listEventsSync("primary", new SyncRequest(token, 100));
        assertThat(incrementalSync.items()).hasSize(1);
        assertThat(incrementalSync.items().getFirst().summary()).isEqualTo("New event");
    }

    @Test
    void listEventsSync_incrementalSync_detectsDeletes() {
        var created = platform.createEvent("primary", new EventDetails("Delete me", null, null,
                                                                       new EventTiming.Timed(
                                                                               Instant.parse("2026-07-26T10:00:00Z"),
                                                                               Instant.parse("2026-07-26T11:00:00Z"),
                                                                               ZoneId.of("UTC")),
                                                                       List.of()));

        var fullSync = platform.listEventsSync("primary", SyncRequest.initial(100));
        var token    = fullSync.syncToken();

        platform.deleteEvent("primary", created.id());

        var incrementalSync = platform.listEventsSync("primary", new SyncRequest(token, 100));
        assertThat(incrementalSync.deletedIds()).contains(created.id());
    }

    @Test
    void listEventsSync_noChanges_returnsEmpty() {
        platform.createEvent("primary", new EventDetails("Stable", null, null,
                                                         new EventTiming.Timed(
                                                                 Instant.parse("2026-07-26T10:00:00Z"),
                                                                 Instant.parse("2026-07-26T11:00:00Z"),
                                                                 ZoneId.of("UTC")),
                                                         List.of()));

        var fullSync = platform.listEventsSync("primary", SyncRequest.initial(100));
        var token    = fullSync.syncToken();

        var incrementalSync = platform.listEventsSync("primary", new SyncRequest(token, 100));
        assertThat(incrementalSync.items()).isEmpty();
        assertThat(incrementalSync.deletedIds()).isEmpty();
    }


}
