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

    private static final CalendarOperations NOOP = new CalendarOperations() {
        @Override
        public List<CalendarInfo> listCalendars()                             {return List.of();}

        @Override
        public List<CalendarEvent> listEvents(String c, Instant f, Instant t) {return List.of();}

        @Override
        public CalendarEvent getEvent(String c, String e) {
            throw new UnsupportedOperationException("No calendar provider configured");
        }

        @Override
        public CalendarEvent createEvent(String c, EventDetails d) {
            throw new UnsupportedOperationException("No calendar provider configured");
        }

        @Override
        public CalendarEvent updateEvent(String c, String e, EventDetails d) {
            throw new UnsupportedOperationException("No calendar provider configured");
        }

        @Override
        public void deleteEvent(String c, String e) {
            throw new UnsupportedOperationException("No calendar provider configured");
        }

        @Override
        public SyncResult<CalendarEvent> listEventsSync(String c, SyncRequest r) {
            throw new UnsupportedOperationException("No calendar provider configured");
        }
    };

    @Override
    public String id() {
        return "none";
    }

    @Override
    public CalendarOperations calendarOps(String userId) {
        return NOOP;
    }
}
