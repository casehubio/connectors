package io.casehub.connectors.graphql.dto;

import java.time.Instant;
import java.util.List;

public record CalendarEventInfo(String id, String calendarId, String summary,
    String description, String location, Instant start, Instant end,
    String timeZone, List<String> attendees) {}
