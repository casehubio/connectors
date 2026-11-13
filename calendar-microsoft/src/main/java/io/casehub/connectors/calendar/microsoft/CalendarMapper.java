package io.casehub.connectors.calendar.microsoft;

import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.EventTiming;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class CalendarMapper {

    private CalendarMapper() {}

    static CalendarInfo toCalendarInfo(JsonObject json) {
        return new CalendarInfo(
                json.getString("id"),
                json.getString("name", ""),
                "",
                json.getBoolean("isDefaultCalendar", false));
    }

    static CalendarEvent toCalendarEvent(JsonObject json, String calendarId) {
        return new CalendarEvent(
                json.getString("id"),
                calendarId,
                json.getString("subject", ""),
                json.getString("bodyPreview", ""),
                locationString(json),
                parseTiming(json),
                extractAttendees(json),
                json.getString("seriesMasterId", null));
    }

    static JsonObject toGraphEvent(EventDetails details) {
        var builder = Json.createObjectBuilder();
        if (details.summary() != null) builder.add("subject", details.summary());
        if (details.description() != null) {
            builder.add("body", Json.createObjectBuilder()
                    .add("contentType", "text")
                    .add("content", details.description()));
        }
        if (details.location() != null) {
            builder.add("location", Json.createObjectBuilder()
                    .add("displayName", details.location()));
        }
        addTimingToJson(builder, details.timing());
        if (details.attendees() != null && !details.attendees().isEmpty()) {
            var arr = Json.createArrayBuilder();
            for (String email : details.attendees()) {
                arr.add(Json.createObjectBuilder()
                        .add("emailAddress", Json.createObjectBuilder()
                                .add("address", email))
                        .add("type", "required"));
            }
            builder.add("attendees", arr);
        }
        return builder.build();
    }

    private static EventTiming parseTiming(JsonObject json) {
        boolean isAllDay = json.getBoolean("isAllDay", false);
        JsonObject start = json.getJsonObject("start");
        JsonObject end = json.getJsonObject("end");
        if (start == null || end == null) {
            return new EventTiming.Timed(Instant.now(), Instant.now(), ZoneId.of("UTC"));
        }

        if (isAllDay) {
            String startDate = start.getString("dateTime", "").substring(0, 10);
            String endDate = end.getString("dateTime", "").substring(0, 10);
            return new EventTiming.AllDay(LocalDate.parse(startDate), LocalDate.parse(endDate));
        }

        String timeZone = start.getString("timeZone", "UTC");
        ZoneId zoneId = ZoneId.of(timeZone);
        Instant startInstant = parseGraphDateTime(start.getString("dateTime", ""), zoneId);
        Instant endInstant = parseGraphDateTime(end.getString("dateTime", ""), zoneId);
        return new EventTiming.Timed(startInstant, endInstant, zoneId);
    }

    private static Instant parseGraphDateTime(String dateTime, ZoneId zoneId) {
        try {
            if (dateTime.endsWith("Z")) {
                return Instant.parse(dateTime);
            }
            return ZonedDateTime.parse(dateTime + "[" + zoneId + "]",
                    DateTimeFormatter.ISO_ZONED_DATE_TIME).toInstant();
        } catch (Exception e) {
            try {
                return ZonedDateTime.of(
                        java.time.LocalDateTime.parse(dateTime),
                        zoneId).toInstant();
            } catch (Exception e2) {
                return Instant.now();
            }
        }
    }

    private static void addTimingToJson(jakarta.json.JsonObjectBuilder builder, EventTiming timing) {
        switch (timing) {
            case EventTiming.Timed t -> {
                builder.add("isAllDay", false);
                builder.add("start", Json.createObjectBuilder()
                        .add("dateTime", t.start().atZone(t.timeZone())
                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .add("timeZone", t.timeZone().getId()));
                builder.add("end", Json.createObjectBuilder()
                        .add("dateTime", t.end().atZone(t.timeZone())
                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .add("timeZone", t.timeZone().getId()));
            }
            case EventTiming.AllDay a -> {
                builder.add("isAllDay", true);
                builder.add("start", Json.createObjectBuilder()
                        .add("dateTime", a.start().atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .add("timeZone", "UTC"));
                builder.add("end", Json.createObjectBuilder()
                        .add("dateTime", a.end().atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .add("timeZone", "UTC"));
            }
        }
    }

    private static String locationString(JsonObject json) {
        JsonObject loc = json.getJsonObject("location");
        if (loc == null) return "";
        return loc.getString("displayName", "");
    }

    private static List<String> extractAttendees(JsonObject json) {
        JsonArray attendees = json.getJsonArray("attendees");
        if (attendees == null) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonValue v : attendees) {
            JsonObject att = v.asJsonObject();
            JsonObject email = att.getJsonObject("emailAddress");
            if (email != null) {
                result.add(email.getString("address", ""));
            }
        }
        return List.copyOf(result);
    }
}
