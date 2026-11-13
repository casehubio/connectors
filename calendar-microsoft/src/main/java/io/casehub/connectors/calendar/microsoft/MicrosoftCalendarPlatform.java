package io.casehub.connectors.calendar.microsoft;

import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.CalendarPlatform;
import io.casehub.connectors.microsoft.TokenRetry;
import io.casehub.platform.api.authn.ServiceConnectionProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class MicrosoftCalendarPlatform implements CalendarPlatform {

    private static final Duration INITIAL_SYNC_PAST = Duration.ofDays(180);
    private static final Duration INITIAL_SYNC_FUTURE = Duration.ofDays(365);

    private final MicrosoftCalendarClient calendarClient;
    private final ServiceConnectionProvider connectionProvider;

    public MicrosoftCalendarPlatform(MicrosoftCalendarClient calendarClient,
            ServiceConnectionProvider connectionProvider) {
        this.calendarClient = calendarClient;
        this.connectionProvider = connectionProvider;
    }

    MicrosoftCalendarPlatform(MicrosoftCalendarClient calendarClient) {
        this(calendarClient, null);
    }

    @Override
    public String id() { return "microsoft"; }

    @Override
    public List<CalendarInfo> listCalendars() {
        return TokenRetry.withTokenRetry(this::resolveToken,
                token -> calendarClient.listCalendars(token).stream()
                        .map(CalendarMapper::toCalendarInfo).toList());
    }

    @Override
    public List<CalendarEvent> listEvents(String calendarId, Instant from, Instant to) {
        return TokenRetry.withTokenRetry(this::resolveToken,
                token -> calendarClient.listEvents(calendarId, from, to, token).stream()
                        .map(json -> CalendarMapper.toCalendarEvent(json, calendarId)).toList());
    }

    @Override
    public CalendarEvent getEvent(String calendarId, String eventId) {
        return TokenRetry.withTokenRetry(this::resolveToken,
                token -> CalendarMapper.toCalendarEvent(
                        calendarClient.getEvent(eventId, token), calendarId));
    }

    @Override
    public CalendarEvent createEvent(String calendarId, EventDetails details) {
        return TokenRetry.withTokenRetry(this::resolveToken,
                token -> CalendarMapper.toCalendarEvent(
                        calendarClient.createEvent(calendarId,
                                CalendarMapper.toGraphEvent(details), token),
                        calendarId));
    }

    @Override
    public CalendarEvent updateEvent(String calendarId, String eventId, EventDetails details) {
        return TokenRetry.withTokenRetry(this::resolveToken,
                token -> CalendarMapper.toCalendarEvent(
                        calendarClient.updateEvent(eventId,
                                CalendarMapper.toGraphEvent(details), token),
                        calendarId));
    }

    @Override
    public void deleteEvent(String calendarId, String eventId) {
        TokenRetry.withTokenRetryVoid(this::resolveToken,
                token -> calendarClient.deleteEvent(eventId, token));
    }

    @Override
    public SyncResult<CalendarEvent> listEventsSync(String calendarId, SyncRequest request) {
        return TokenRetry.withTokenRetry(this::resolveToken,
                token -> {
                    Instant from = Instant.now().minus(INITIAL_SYNC_PAST);
                    Instant to = Instant.now().plus(INITIAL_SYNC_FUTURE);
                    var delta = calendarClient.deltaSync(calendarId,
                            request.syncToken(), from, to, token);
                    return new SyncResult<>(
                            delta.changed().stream()
                                    .map(json -> CalendarMapper.toCalendarEvent(json, calendarId))
                                    .toList(),
                            delta.deletedIds(),
                            delta.deltaLink(),
                            false);
                });
    }

    private String resolveToken() {
        if (connectionProvider == null) return "test-token";
        return connectionProvider.getAccessToken(null, "microsoft", "default")
                .accessToken();
    }
}
