package io.casehub.connectors.calendar;

import io.casehub.connectors.calendar.spi.CalendarPlatform;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@ApplicationScoped
public class CalendarBeans {

    @Produces
    @ApplicationScoped
    public CalendarPlatformService calendarPlatformService(@All List<CalendarPlatform> platforms) {
        return new CalendarPlatformService(platforms);
    }
}
