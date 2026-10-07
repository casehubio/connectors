package io.casehub.connectors.calendar.google;

import io.casehub.platform.api.authn.ServiceConnectionProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class CalendarGoogleBeans {

    @Produces
    @ApplicationScoped
    public GoogleCalendarPlatform googleCalendarPlatform(ServiceConnectionProvider connectionProvider) {
        return new GoogleCalendarPlatform(connectionProvider);
    }
}
