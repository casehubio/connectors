package io.casehub.connectors.calendar.google;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CalendarGoogleBeans {

    @Produces
    @ApplicationScoped
    public GoogleCalendarPlatform googleCalendarPlatform(
            @ConfigProperty(name = "casehub.connectors.calendar.google.client-id", defaultValue = "") String clientId,
            @ConfigProperty(name = "casehub.connectors.calendar.google.client-secret", defaultValue = "") String clientSecret,
            @ConfigProperty(name = "casehub.connectors.calendar.google.refresh-token", defaultValue = "") String refreshToken) {
        return new GoogleCalendarPlatform(clientId, clientSecret, refreshToken);
    }
}
