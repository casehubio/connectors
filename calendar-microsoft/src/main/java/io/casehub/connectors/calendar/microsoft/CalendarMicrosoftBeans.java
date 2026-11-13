package io.casehub.connectors.calendar.microsoft;

import io.casehub.connectors.microsoft.MicrosoftGraphClient;
import io.casehub.platform.api.authn.ServiceConnectionProvider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class CalendarMicrosoftBeans {

    @Produces
    @ApplicationScoped
    MicrosoftCalendarPlatform microsoftCalendarPlatform(MicrosoftGraphClient graph,
            ServiceConnectionProvider connectionProvider) {
        return new MicrosoftCalendarPlatform(new MicrosoftCalendarClient(graph),
                connectionProvider);
    }
}
