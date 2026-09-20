package io.casehub.connectors.graphql.dto;

import java.util.List;

public record CalendarEventRequest(String summary, String description,
    String location, String start, String end, String timeZone,
    String startDate, String endDate, List<String> attendees) {}
