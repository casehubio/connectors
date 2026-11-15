package io.casehub.connectors.calendar.spi;

import io.casehub.connectors.PlatformExtensible;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.platform.simulation.SimulationEligible;

import java.time.Instant;
import java.util.List;

@SimulationEligible(name = "calendar-platform")
public interface CalendarPlatform extends PlatformExtensible {

    String id();

    CalendarOperations calendarOps(String userId);

    interface CalendarOperations {
        List<CalendarInfo> listCalendars();

        List<CalendarEvent> listEvents(String calendarId, Instant from, Instant to);

        CalendarEvent getEvent(String calendarId, String eventId);

        CalendarEvent createEvent(String calendarId, EventDetails details);

        CalendarEvent updateEvent(String calendarId, String eventId, EventDetails details);

        void deleteEvent(String calendarId, String eventId);

        SyncResult<CalendarEvent> listEventsSync(String calendarId, SyncRequest request);
    }
}
