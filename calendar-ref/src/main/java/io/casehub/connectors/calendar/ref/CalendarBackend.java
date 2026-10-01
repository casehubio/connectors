package io.casehub.connectors.calendar.ref;

import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.model.EventDetails;

import java.time.Instant;
import java.util.List;

public interface CalendarBackend {

    List<CalendarInfo> listCalendars();

    List<CalendarEvent> listEvents(String calendarId, Instant from, Instant to);

    CalendarEvent getEvent(String calendarId, String eventId);

    CalendarEvent createEvent(String calendarId, EventDetails details);

    CalendarEvent updateEvent(String calendarId, String eventId, EventDetails details);

    void deleteEvent(String calendarId, String eventId);

    long currentVersion();

    List<CalendarEvent> changedSince(String calendarId, long version);

    List<String> deletedSince(String calendarId, long version);

}
