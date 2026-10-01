package io.casehub.connectors.calendar.ref;

import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.CalendarPlatform;

import java.time.Instant;
import java.util.List;

public class RefCalendarPlatform implements CalendarPlatform {

    private final CalendarBackend backend;

    public RefCalendarPlatform(CalendarBackend backend) {
        this.backend = backend;
    }

    @Override
    public String id() {
        return "ref";
    }

    @Override
    public List<CalendarInfo> listCalendars() {
        return backend.listCalendars();
    }

    @Override
    public List<CalendarEvent> listEvents(String calendarId, Instant from, Instant to) {
        return backend.listEvents(calendarId, from, to);
    }

    @Override
    public CalendarEvent getEvent(String calendarId, String eventId) {
        return backend.getEvent(calendarId, eventId);
    }

    @Override
    public CalendarEvent createEvent(String calendarId, EventDetails details) {
        return backend.createEvent(calendarId, details);
    }

    @Override
    public CalendarEvent updateEvent(String calendarId, String eventId, EventDetails details) {
        return backend.updateEvent(calendarId, eventId, details);
    }

    @Override
    public void deleteEvent(String calendarId, String eventId) {
        backend.deleteEvent(calendarId, eventId);
    }

    @Override
    public SyncResult<CalendarEvent> listEventsSync(String calendarId, SyncRequest request) {
        long sinceVersion = request.syncToken() != null
                            ? Long.parseLong(request.syncToken()) : 0;
        var changed = backend.changedSince(calendarId, sinceVersion);
        var deleted = backend.deletedSince(calendarId, sinceVersion);
        return new SyncResult<>(changed, deleted,
                                String.valueOf(backend.currentVersion()), false);
    }

}
