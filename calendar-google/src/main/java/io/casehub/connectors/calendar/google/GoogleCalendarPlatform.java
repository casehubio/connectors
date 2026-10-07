package io.casehub.connectors.calendar.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.Events;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.SyncTokenExpiredException;
import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.CalendarPlatform;
import io.casehub.platform.api.authn.RequiresScopes;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@RequiresScopes(provider = "google",
    scopes = {"https://www.googleapis.com/auth/calendar.readonly",
              "https://www.googleapis.com/auth/calendar.events"})
public class GoogleCalendarPlatform implements CalendarPlatform {

    private static final Logger LOG             = Logger.getLogger(GoogleCalendarPlatform.class);
    private static final int    MAX_PAGES       = 20;
    private static final String DEFAULT_TENANCY = "default";

    private final ServiceConnectionProvider connectionProvider;
    private final NetHttpTransport transport;
    private       Calendar         calendarService;

    public GoogleCalendarPlatform(ServiceConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        try {
            this.transport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize HTTP transport", e);
        }
    }

    GoogleCalendarPlatform(Calendar calendarService) {
        this.connectionProvider = null;
        this.transport          = null;
        this.calendarService    = calendarService;
    }

    Calendar buildService(String actorId) {
        var token = connectionProvider.getAccessToken(actorId, "google", DEFAULT_TENANCY);
        var credentials = GoogleCredentials.create(
            new AccessToken(token.accessToken(), Date.from(token.expiresAt())));
        return new Calendar.Builder(transport, GsonFactory.getDefaultInstance(),
                                    new HttpCredentialsAdapter(credentials))
                       .setApplicationName("casehub-connectors")
                       .build();
    }

    @Override
    public String id() {
        return "google";
    }

    @Override
    public List<CalendarInfo> listCalendars() {
        requireClient();
        try {
            CalendarList list = calendarService.calendarList().list().execute();
            if (list.getItems() == null) {return List.of();}
            return list.getItems().stream()
                       .map(e -> new CalendarInfo(e.getId(), e.getSummary(),
                                                  e.getDescription(), Boolean.TRUE.equals(e.getPrimary())))
                       .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list calendars", e);
        }
    }

    @Override
    public List<CalendarEvent> listEvents(String calendarId, Instant from, Instant to) {
        requireClient();
        List<CalendarEvent> result = new ArrayList<>();
        try {
            String pageToken = null;
            int    page      = 0;
            while (page < MAX_PAGES) {
                Events response = calendarService.events().list(calendarId)
                                                 .setSingleEvents(true)
                                                 .setOrderBy("startTime")
                                                 .setTimeMin(new DateTime(from.toEpochMilli()))
                                                 .setTimeMax(new DateTime(to.toEpochMilli()))
                                                 .setPageToken(pageToken)
                                                 .execute();
                if (response.getItems() != null) {
                    for (var event : response.getItems()) {
                        result.add(GoogleEventMapper.toCalendarEvent(event, calendarId));
                    }
                }
                pageToken = response.getNextPageToken();
                if (pageToken == null) {break;}
                page++;
            }
            if (page >= MAX_PAGES) {
                LOG.warnf("listEvents hit MAX_PAGES (%d) for calendar '%s' — results may be incomplete",
                          MAX_PAGES, calendarId);
            }
        } catch (IOException e) {
            LOG.warnf(e, "listEvents failed mid-pagination for calendar '%s' — returning %d partial results",
                      calendarId, result.size());
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public CalendarEvent getEvent(String calendarId, String eventId) {
        requireClient();
        try {
            var event = calendarService.events().get(calendarId, eventId).execute();
            return GoogleEventMapper.toCalendarEvent(event, calendarId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get event " + eventId, e);
        }
    }

    @Override
    public CalendarEvent createEvent(String calendarId, EventDetails details) {
        requireClient();
        try {
            var googleEvent = GoogleEventMapper.toGoogleEvent(details);
            var created     = calendarService.events().insert(calendarId, googleEvent).execute();
            return GoogleEventMapper.toCalendarEvent(created, calendarId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create event", e);
        }
    }

    @Override
    public CalendarEvent updateEvent(String calendarId, String eventId, EventDetails details) {
        requireClient();
        try {
            var googleEvent = GoogleEventMapper.toGoogleEvent(details);
            var updated     = calendarService.events().update(calendarId, eventId, googleEvent).execute();
            return GoogleEventMapper.toCalendarEvent(updated, calendarId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to update event " + eventId, e);
        }
    }

    @Override
    public void deleteEvent(String calendarId, String eventId) {
        requireClient();
        try {
            calendarService.events().delete(calendarId, eventId).execute();
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete event " + eventId, e);
        }
    }

    @Override
    public SyncResult<CalendarEvent> listEventsSync(String calendarId, SyncRequest request) {
        requireClient();
        List<CalendarEvent> items      = new ArrayList<>();
        List<String>        deletedIds = new ArrayList<>();
        String              syncToken  = null;
        try {
            String pageToken = null;
            int    page      = 0;
            while (page < MAX_PAGES) {
                var req = calendarService.events().list(calendarId)
                                         .setPageToken(pageToken);
                if (request.syncToken() != null) {
                    req.setSyncToken(request.syncToken());
                } else {
                    req.setSingleEvents(true);
                }
                if (request.pageSize() > 0) {
                    req.setMaxResults(request.pageSize());
                }
                Events response = req.execute();
                if (response.getItems() != null) {
                    for (var event : response.getItems()) {
                        if ("cancelled".equals(event.getStatus())) {
                            deletedIds.add(event.getId());
                        } else {
                            items.add(GoogleEventMapper.toCalendarEvent(event, calendarId));
                        }
                    }
                }
                if (response.getNextSyncToken() != null) {
                    syncToken = response.getNextSyncToken();
                }
                pageToken = response.getNextPageToken();
                if (pageToken == null) {break;}
                page++;
            }
            if (page >= MAX_PAGES) {
                LOG.warnf("listEventsSync hit MAX_PAGES (%d) for calendar '%s' — %d items, %d deletes accumulated",
                          MAX_PAGES, calendarId, items.size(), deletedIds.size());
            }
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 410) {
                throw new SyncTokenExpiredException(request.syncToken());
            }
            if (!items.isEmpty()) {
                LOG.warnf(e, "listEventsSync failed mid-pagination for calendar '%s' — returning %d partial items",
                          calendarId, items.size());
                return new SyncResult<>(Collections.unmodifiableList(items),
                                        Collections.unmodifiableList(deletedIds), syncToken, false);
            }
            throw new RuntimeException("Google Calendar sync failed for calendar " + calendarId, e);
        } catch (IOException e) {
            if (!items.isEmpty()) {
                LOG.warnf(e, "listEventsSync failed mid-pagination for calendar '%s' — returning %d partial items",
                          calendarId, items.size());
                return new SyncResult<>(Collections.unmodifiableList(items),
                                        Collections.unmodifiableList(deletedIds), syncToken, false);
            }
            throw new RuntimeException("Google Calendar sync failed for calendar " + calendarId, e);
        }
        return new SyncResult<>(Collections.unmodifiableList(items),
                                Collections.unmodifiableList(deletedIds), syncToken, false);
    }

    private void requireClient() {
        if (calendarService == null) {
            throw new IllegalStateException(
                    "Google Calendar client not initialised — check credentials configuration");
        }
    }
}
