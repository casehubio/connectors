package io.casehub.connectors.graphql;

import io.casehub.connectors.calendar.CalendarPlatformService;
import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.CalendarPlatform;
import io.casehub.connectors.calendar.spi.EventTiming;
import io.casehub.connectors.graphql.dto.CalendarEventInfo;
import io.casehub.connectors.graphql.dto.CalendarEventRequest;
import io.casehub.connectors.graphql.dto.OperationResult;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@McpDomain(value = "connectors/calendar", basePath = "/api/connectors/calendar")
@ApplicationScoped
public class ConnectorCalendarApi {

    @Inject CalendarPlatformService calendarService;

    @PlatformQuery("List available calendars on a platform")
    @RestPath("/calendars")
    public List<io.casehub.connectors.calendar.model.CalendarInfo> listCalendars(
            @QueryParam("platform") String platform) {
        CalendarPlatform p = calendarService.platform(platform);
        if (p == null) return List.of();
        return p.listCalendars();
    }

    @PlatformQuery("List events in a calendar")
    @RestPath("/events")
    public List<CalendarEventInfo> listEvents(
            @QueryParam("platform") String platform,
            @QueryParam("calendarId") String calendarId,
            @QueryParam("from") String from,
            @QueryParam("to") String to) {
        CalendarPlatform p = calendarService.platform(platform);
        if (p == null) return List.of();
        Instant start = from != null ? Instant.parse(from) : Instant.now();
        Instant end = to != null ? Instant.parse(to) : start.plusSeconds(86400);
        return p.listEvents(calendarId, start, end).stream()
            .map(this::toEventInfo).toList();
    }

    @PlatformQuery("Get a single calendar event")
    @RestPath("/events/{eventId}")
    public CalendarEventInfo getEvent(
            @QueryParam("platform") String platform,
            @QueryParam("calendarId") String calendarId,
            @PathParam String eventId) {
        CalendarPlatform p = calendarService.platform(platform);
        if (p == null) return null;
        CalendarEvent event = p.getEvent(calendarId, eventId);
        return event != null ? toEventInfo(event) : null;
    }

    @PlatformMutation("Create a calendar event")
    @RestPath("/events")
    public CalendarEventInfo createEvent(
            @QueryParam("platform") String platform,
            @QueryParam("calendarId") String calendarId,
            CalendarEventRequest request) {
        CalendarPlatform p = calendarService.platform(platform);
        if (p == null) return null;
        EventTiming timing = buildTiming(request);
        EventDetails details = new EventDetails(request.summary(),
            request.description(), request.location(), timing,
            request.attendees());
        CalendarEvent created = p.createEvent(calendarId, details);
        return toEventInfo(created);
    }

    @PlatformMutation("Update a calendar event")
    @RestPath("/events/{eventId}")
    public CalendarEventInfo updateEvent(
            @QueryParam("platform") String platform,
            @QueryParam("calendarId") String calendarId,
            @PathParam String eventId,
            CalendarEventRequest request) {
        CalendarPlatform p = calendarService.platform(platform);
        if (p == null) return null;
        EventTiming timing = buildTiming(request);
        EventDetails details = new EventDetails(request.summary(),
            request.description(), request.location(), timing,
            request.attendees());
        CalendarEvent updated = p.updateEvent(calendarId, eventId, details);
        return toEventInfo(updated);
    }

    @PlatformMutation("Delete a calendar event")
    @RestPath("/events/{eventId}/delete")
    public OperationResult deleteEvent(
            @QueryParam("platform") String platform,
            @QueryParam("calendarId") String calendarId,
            @PathParam String eventId) {
        CalendarPlatform p = calendarService.platform(platform);
        if (p == null) return new OperationResult(false, platform, calendarId, "Unknown platform");
        try {
            p.deleteEvent(calendarId, eventId);
            return new OperationResult(true, platform, calendarId, "Deleted " + eventId);
        } catch (Exception e) {
            return new OperationResult(false, platform, calendarId, e.getMessage());
        }
    }

    private CalendarEventInfo toEventInfo(CalendarEvent e) {
        Instant start = null, end = null;
        String tz = null;
        if (e.timing() instanceof EventTiming.Timed t) {
            start = t.start(); end = t.end(); tz = t.timeZone().getId();
        } else if (e.timing() instanceof EventTiming.AllDay a) {
            start = a.start().atStartOfDay(ZoneId.of("UTC")).toInstant();
            end = a.end().atStartOfDay(ZoneId.of("UTC")).toInstant();
        }
        return new CalendarEventInfo(e.id(), e.calendarId(), e.summary(),
            e.description(), e.location(), start, end, tz, e.attendees());
    }

    private static EventTiming buildTiming(CalendarEventRequest req) {
        if (req.startDate() != null && !req.startDate().isBlank()) {
            LocalDate start = LocalDate.parse(req.startDate());
            LocalDate end = req.endDate() != null ? LocalDate.parse(req.endDate()) : start.plusDays(1);
            return new EventTiming.AllDay(start, end);
        }
        ZoneId zone = req.timeZone() != null ? ZoneId.of(req.timeZone()) : ZoneId.of("UTC");
        Instant start = req.start() != null ? Instant.parse(req.start()) : Instant.now();
        Instant end = req.end() != null ? Instant.parse(req.end()) : start.plusSeconds(3600);
        return new EventTiming.Timed(start, end, zone);
    }
}
