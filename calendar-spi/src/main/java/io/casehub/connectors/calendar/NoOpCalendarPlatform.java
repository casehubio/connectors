package io.casehub.connectors.calendar;

import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.CalendarPlatform;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpCalendarPlatform implements CalendarPlatform {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public List<CalendarInfo> listCalendars() {
        return List.of();
    }

    @Override
    public List<CalendarEvent> listEvents(final String calendarId,
            final Instant from, final Instant to) {
        return List.of();
    }

    @Override
    public CalendarEvent getEvent(final String calendarId, final String eventId) {
        throw new UnsupportedOperationException("No calendar provider configured");
    }

    @Override
    public CalendarEvent createEvent(final String calendarId, final EventDetails details) {
        throw new UnsupportedOperationException("No calendar provider configured");
    }

    @Override
    public CalendarEvent updateEvent(final String calendarId, final String eventId,
            final EventDetails details) {
        throw new UnsupportedOperationException("No calendar provider configured");
    }

    @Override
    public void deleteEvent(final String calendarId, final String eventId) {
        throw new UnsupportedOperationException("No calendar provider configured");
    }

    @Override
    public SyncResult<CalendarEvent> listEventsSync(final String calendarId,
                                                    final SyncRequest request) {
        throw new UnsupportedOperationException("No calendar provider configured");
    }

}
