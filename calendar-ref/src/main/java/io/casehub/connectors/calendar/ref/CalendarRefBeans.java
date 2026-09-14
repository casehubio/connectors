package io.casehub.connectors.calendar.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class CalendarRefBeans {

    @Produces
    @ApplicationScoped
    public RefCalendarPlatform refCalendarPlatform(CalendarBackend backend) {
        return new RefCalendarPlatform(backend);
    }
}
