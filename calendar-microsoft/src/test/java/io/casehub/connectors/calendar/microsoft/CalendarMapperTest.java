package io.casehub.connectors.calendar.microsoft;

import io.casehub.connectors.calendar.spi.EventTiming;
import jakarta.json.Json;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class CalendarMapperTest {

    @Test
    void toCalendarInfoMapsFields() {
        var json = Json.createObjectBuilder()
                .add("id", "cal-1")
                .add("name", "My Calendar")
                .add("isDefaultCalendar", true)
                .build();
        var info = CalendarMapper.toCalendarInfo(json);
        assertThat(info.id()).isEqualTo("cal-1");
        assertThat(info.summary()).isEqualTo("My Calendar");
        assertThat(info.primary()).isTrue();
    }

    @Test
    void toCalendarEventMapsTimedEvent() {
        var json = Json.createObjectBuilder()
                .add("id", "evt-1")
                .add("subject", "Meeting")
                .add("bodyPreview", "Discussion")
                .add("location", Json.createObjectBuilder()
                        .add("displayName", "Room 101"))
                .add("isAllDay", false)
                .add("start", Json.createObjectBuilder()
                        .add("dateTime", "2026-11-13T14:00:00.0000000")
                        .add("timeZone", "UTC"))
                .add("end", Json.createObjectBuilder()
                        .add("dateTime", "2026-11-13T15:00:00.0000000")
                        .add("timeZone", "UTC"))
                .add("attendees", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("emailAddress", Json.createObjectBuilder()
                                        .add("address", "bob@example.com"))
                                .add("type", "required")))
                .build();
        var event = CalendarMapper.toCalendarEvent(json, "cal-1");
        assertThat(event.id()).isEqualTo("evt-1");
        assertThat(event.calendarId()).isEqualTo("cal-1");
        assertThat(event.summary()).isEqualTo("Meeting");
        assertThat(event.location()).isEqualTo("Room 101");
        assertThat(event.timing()).isInstanceOf(EventTiming.Timed.class);
        assertThat(event.attendees()).containsExactly("bob@example.com");
    }

    @Test
    void toCalendarEventMapsAllDayEvent() {
        var json = Json.createObjectBuilder()
                .add("id", "evt-2")
                .add("subject", "Holiday")
                .add("isAllDay", true)
                .add("start", Json.createObjectBuilder()
                        .add("dateTime", "2026-12-25T00:00:00.0000000")
                        .add("timeZone", "UTC"))
                .add("end", Json.createObjectBuilder()
                        .add("dateTime", "2026-12-26T00:00:00.0000000")
                        .add("timeZone", "UTC"))
                .build();
        var event = CalendarMapper.toCalendarEvent(json, "cal-1");
        assertThat(event.timing()).isInstanceOf(EventTiming.AllDay.class);
        var allDay = (EventTiming.AllDay) event.timing();
        assertThat(allDay.start()).isEqualTo(LocalDate.of(2026, 12, 25));
        assertThat(allDay.end()).isEqualTo(LocalDate.of(2026, 12, 26));
    }

    @Test
    void toGraphEventConvertsTimedDetails() {
        var timing = new EventTiming.Timed(
                java.time.Instant.parse("2026-11-13T14:00:00Z"),
                java.time.Instant.parse("2026-11-13T15:00:00Z"),
                java.time.ZoneId.of("UTC"));
        var details = new io.casehub.connectors.calendar.model.EventDetails(
                "Meeting", "Notes", "Room 1", timing, java.util.List.of("a@b.com"));
        var json = CalendarMapper.toGraphEvent(details);
        assertThat(json.getString("subject")).isEqualTo("Meeting");
        assertThat(json.getBoolean("isAllDay")).isFalse();
        assertThat(json.getJsonObject("start").getString("timeZone")).isEqualTo("UTC");
        assertThat(json.getJsonArray("attendees")).hasSize(1);
    }

    @Test
    void toCalendarEventHandlesMissingLocation() {
        var json = Json.createObjectBuilder()
                .add("id", "evt-3")
                .add("subject", "No location")
                .add("isAllDay", false)
                .add("start", Json.createObjectBuilder()
                        .add("dateTime", "2026-11-13T10:00:00")
                        .add("timeZone", "UTC"))
                .add("end", Json.createObjectBuilder()
                        .add("dateTime", "2026-11-13T11:00:00")
                        .add("timeZone", "UTC"))
                .build();
        var event = CalendarMapper.toCalendarEvent(json, "cal-1");
        assertThat(event.location()).isEmpty();
    }
}
