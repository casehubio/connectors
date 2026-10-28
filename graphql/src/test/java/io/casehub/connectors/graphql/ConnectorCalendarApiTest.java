package io.casehub.connectors.graphql;

import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.calendar.CalendarPlatformService;
import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.CalendarPlatform;
import io.casehub.connectors.calendar.spi.EventTiming;
import io.casehub.connectors.graphql.dto.CalendarEventRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorCalendarApiTest {

    private static final Instant NOW = Instant.parse("2026-12-20T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);

    @Test
    void listCalendars_delegatesToPlatform() {
        var api = apiWith(fullPlatform());

        var result = api.listCalendars("stub-cal");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo("cal-1");
        assertThat(result.getFirst().summary()).isEqualTo("Work");
    }

    @Test
    void listCalendars_unknownPlatformThrows() {
        var api = apiWith(fullPlatform());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> api.listCalendars("unknown"));
    }

    @Test
    void listEvents_returnsEventsInRange() {
        var api = apiWith(fullPlatform());

        var result = api.listEvents("stub-cal", "cal-1",
                NOW.toString(), LATER.plusSeconds(7200).toString());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().summary()).isEqualTo("Team standup");
    }

    @Test
    void getEvent_returnsMatchingEvent() {
        var api = apiWith(fullPlatform());

        var result = api.getEvent("stub-cal", "cal-1", "evt-1");

        assertThat(result).isNotNull();
        assertThat(result.summary()).isEqualTo("Team standup");
        assertThat(result.timeZone()).isEqualTo("UTC");
    }

    @Test
    void createEvent_delegatesAndReturnsCreated() {
        var api = apiWith(fullPlatform());
        var request = new CalendarEventRequest("New meeting", "Description",
                "Room 1", NOW.plusSeconds(7200).toString(),
                NOW.plusSeconds(10800).toString(), "UTC",
                null, null, List.of("alice@example.com"));

        var result = api.createEvent("stub-cal", "cal-1", request);

        assertThat(result).isNotNull();
        assertThat(result.summary()).isEqualTo("New meeting");
        assertThat(result.calendarId()).isEqualTo("cal-1");
    }

    @Test
    void updateEvent_delegatesAndReturnsUpdated() {
        var api = apiWith(fullPlatform());
        var request = new CalendarEventRequest("Updated standup", null,
                null, NOW.toString(), LATER.toString(), "UTC",
                null, null, null);

        var result = api.updateEvent("stub-cal", "cal-1", "evt-1", request);

        assertThat(result).isNotNull();
        assertThat(result.summary()).isEqualTo("Updated standup");
    }

    @Test
    void deleteEvent_returnsSuccess() {
        var api = apiWith(fullPlatform());

        var result = api.deleteEvent("stub-cal", "cal-1", "evt-1");

        assertThat(result.dispatched()).isTrue();
    }

    @Test
    void syncEvents_returnsChangedEvents() {
        var api = apiWith(fullPlatform());

        var result = api.syncEvents("stub-cal", "cal-1", null, 50);

        assertThat(result.items()).hasSize(1);
        assertThat(result.syncToken()).isEqualTo("1");
    }

    private ConnectorCalendarApi apiWith(CalendarPlatform platform) {
        var service = new CalendarPlatformService(List.of(platform));
        var api = new ConnectorCalendarApi();
        api.calendarService = service;
        return api;
    }

    private static CalendarPlatform fullPlatform() {
        return new StubCalendarPlatform();
    }

    static class StubCalendarPlatform implements CalendarPlatform {
        private final List<CalendarEvent> events = new ArrayList<>();

        StubCalendarPlatform() {
            events.add(new CalendarEvent("evt-1", "cal-1", "Team standup",
                    "Daily sync", "Room A",
                    new EventTiming.Timed(NOW, LATER, ZoneId.of("UTC")),
                    List.of("bob@example.com"), null));
        }

        @Override public String id() { return "stub-cal"; }

        @Override public List<CalendarInfo> listCalendars() {
            return List.of(new CalendarInfo("cal-1", "Work", "Work calendar", true));
        }

        @Override public List<CalendarEvent> listEvents(String calendarId, Instant from, Instant to) {
            return events.stream()
                    .filter(e -> e.calendarId().equals(calendarId))
                    .toList();
        }

        @Override public CalendarEvent getEvent(String calendarId, String eventId) {
            return events.stream()
                    .filter(e -> e.id().equals(eventId))
                    .findFirst().orElse(null);
        }

        @Override public CalendarEvent createEvent(String calendarId, EventDetails details) {
            var event = new CalendarEvent(UUID.randomUUID().toString(), calendarId,
                    details.summary(), details.description(), details.location(),
                    details.timing(), details.attendees(), null);
            events.add(event);
            return event;
        }

        @Override public CalendarEvent updateEvent(String calendarId, String eventId, EventDetails details) {
            var updated = new CalendarEvent(eventId, calendarId,
                    details.summary(), details.description(), details.location(),
                    details.timing(), details.attendees(), null);
            events.removeIf(e -> e.id().equals(eventId));
            events.add(updated);
            return updated;
        }

        @Override public void deleteEvent(String calendarId, String eventId) {
            events.removeIf(e -> e.id().equals(eventId));
        }

        @Override public SyncResult<CalendarEvent> listEventsSync(String calendarId, SyncRequest request) {
            return new SyncResult<>(List.copyOf(events), List.of(), "1", false);
        }
    }
}
