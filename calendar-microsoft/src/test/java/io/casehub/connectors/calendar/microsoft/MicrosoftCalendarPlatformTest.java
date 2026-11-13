package io.casehub.connectors.calendar.microsoft;

import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.EventTiming;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;
import jakarta.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MicrosoftCalendarPlatformTest {

    private MicrosoftCalendarPlatform platform;

    @BeforeEach
    void setUp() {
        var graph = new MicrosoftGraphClient(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/me/calendars") && !uri.contains("calendarView")
                    && !uri.contains("/events")) {
                return new StubHttpResponse(200, """
                        {"value":[
                            {"id":"cal-1","name":"Calendar","isDefaultCalendar":true}
                        ]}""");
            }
            if (uri.contains("calendarView") && !uri.contains("delta")) {
                return new StubHttpResponse(200, """
                        {"value":[%s]}""".formatted(eventJson("evt-1", "Meeting")));
            }
            if (uri.contains("delta")) {
                return new StubHttpResponse(200, """
                        {"value":[%s],"@odata.deltaLink":"https://graph.microsoft.com/v1.0/delta?token=abc"}"""
                        .formatted(eventJson("evt-1", "Meeting")));
            }
            if (uri.contains("/me/events/") && request.method().equals("GET")) {
                return new StubHttpResponse(200, eventJson("evt-1", "Meeting"));
            }
            if (uri.contains("/events") && request.method().equals("POST")) {
                return new StubHttpResponse(201, eventJson("evt-new", "New Event"));
            }
            if (uri.contains("/me/events/") && request.method().equals("PATCH")) {
                return new StubHttpResponse(200, eventJson("evt-1", "Updated"));
            }
            if (uri.contains("/me/events/") && request.method().equals("DELETE")) {
                return new StubHttpResponse(204, "");
            }
            return new StubHttpResponse(200, """
                    {"value":[]}""");
        });
        platform = new MicrosoftCalendarPlatform(new MicrosoftCalendarClient(graph));
    }

    @Test
    void idReturnsMicrosoft() {
        assertThat(platform.id()).isEqualTo("microsoft");
    }

    @Test
    void listCalendarsReturnsMapped() {
        var cals = platform.listCalendars();
        assertThat(cals).hasSize(1);
        assertThat(cals.get(0).summary()).isEqualTo("Calendar");
        assertThat(cals.get(0).primary()).isTrue();
    }

    @Test
    void listEventsReturnsMapped() {
        var events = platform.listEvents("cal-1",
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-30T00:00:00Z"));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).summary()).isEqualTo("Meeting");
        assertThat(events.get(0).calendarId()).isEqualTo("cal-1");
    }

    @Test
    void getEventReturnsMapped() {
        var event = platform.getEvent("cal-1", "evt-1");
        assertThat(event.id()).isEqualTo("evt-1");
        assertThat(event.summary()).isEqualTo("Meeting");
    }

    @Test
    void createEventReturnsMapped() {
        var timing = new EventTiming.Timed(
                Instant.parse("2026-11-13T14:00:00Z"),
                Instant.parse("2026-11-13T15:00:00Z"),
                ZoneId.of("UTC"));
        var details = new EventDetails("New Event", "desc", "Room", timing, List.of());
        var event = platform.createEvent("cal-1", details);
        assertThat(event.id()).isEqualTo("evt-new");
        assertThat(event.summary()).isEqualTo("New Event");
    }

    @Test
    void updateEventReturnsMapped() {
        var timing = new EventTiming.Timed(
                Instant.parse("2026-11-13T14:00:00Z"),
                Instant.parse("2026-11-13T15:00:00Z"),
                ZoneId.of("UTC"));
        var details = new EventDetails("Updated", "desc", "Room", timing, List.of());
        var event = platform.updateEvent("cal-1", "evt-1", details);
        assertThat(event.summary()).isEqualTo("Updated");
    }

    @Test
    void deleteEventDoesNotThrow() {
        assertThatCode(() -> platform.deleteEvent("cal-1", "evt-1"))
                .doesNotThrowAnyException();
    }

    @Test
    void listEventsSyncReturnsSyncResult() {
        var result = platform.listEventsSync("cal-1", SyncRequest.initial(50));
        assertThat(result.items()).hasSize(1);
        assertThat(result.syncToken()).contains("delta?token=abc");
        assertThat(result.deletedIds()).isEmpty();
    }

    private static String eventJson(String id, String subject) {
        return """
                {"id":"%s","subject":"%s","bodyPreview":"","isAllDay":false,"start":{"dateTime":"2026-11-13T14:00:00.0000000","timeZone":"UTC"},"end":{"dateTime":"2026-11-13T15:00:00.0000000","timeZone":"UTC"}}"""
                .formatted(id, subject);
    }
}
