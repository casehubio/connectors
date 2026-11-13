package io.casehub.connectors.calendar.microsoft;

import io.casehub.connectors.Page;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

class MicrosoftCalendarClient {

    private final MicrosoftGraphClient graph;

    MicrosoftCalendarClient(MicrosoftGraphClient graph) {
        this.graph = graph;
    }

    List<JsonObject> listCalendars(String accessToken) {
        return graph.getPage("/me/calendars", accessToken,
                json -> json, null).items();
    }

    List<JsonObject> listEvents(String calendarId, Instant from, Instant to,
            String accessToken) {
        String startDt = DateTimeFormatter.ISO_INSTANT.format(from);
        String endDt = DateTimeFormatter.ISO_INSTANT.format(to);
        String path = "/me/calendars/" + calendarId
                + "/calendarView?startDateTime=" + startDt
                + "&endDateTime=" + endDt;
        List<JsonObject> all = new ArrayList<>();
        String cursor = null;
        do {
            var page = graph.getPage(path, accessToken, json -> json, cursor);
            all.addAll(page.items());
            cursor = page.nextCursor();
        } while (cursor != null);
        return List.copyOf(all);
    }

    JsonObject getEvent(String eventId, String accessToken) {
        return graph.get("/me/events/" + eventId, accessToken);
    }

    JsonObject createEvent(String calendarId, JsonObject body, String accessToken) {
        return graph.post("/me/calendars/" + calendarId + "/events", body, accessToken);
    }

    JsonObject updateEvent(String eventId, JsonObject body, String accessToken) {
        return graph.patch("/me/events/" + eventId, body, accessToken);
    }

    void deleteEvent(String eventId, String accessToken) {
        graph.delete("/me/events/" + eventId, accessToken);
    }

    DeltaResult deltaSync(String calendarId, String syncToken,
            Instant from, Instant to, String accessToken) {
        String path;
        if (syncToken != null) {
            path = "/me/calendarView/delta";
        } else {
            String startDt = DateTimeFormatter.ISO_INSTANT.format(from);
            String endDt = DateTimeFormatter.ISO_INSTANT.format(to);
            path = "/me/calendarView/delta?startDateTime=" + startDt
                    + "&endDateTime=" + endDt;
        }

        List<JsonObject> changed = new ArrayList<>();
        List<String> deletedIds = new ArrayList<>();
        String cursor = syncToken;
        String deltaLink = null;

        do {
            var page = graph.getPage(path, accessToken, json -> json, cursor);
            for (JsonObject item : page.items()) {
                if (item.containsKey("@removed")) {
                    deletedIds.add(item.getString("id"));
                } else {
                    changed.add(item);
                }
            }

            if (page.hasMore()) {
                cursor = page.nextCursor();
            } else {
                deltaLink = page.nextCursor();
                cursor = null;
            }
        } while (cursor != null);

        return new DeltaResult(List.copyOf(changed), List.copyOf(deletedIds), deltaLink);
    }

    record DeltaResult(List<JsonObject> changed, List<String> deletedIds, String deltaLink) {}
}
