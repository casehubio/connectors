package io.casehub.connectors.calendar.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

final class SeedLoader {

    private static final ObjectMapper YAML = YamlMappers.create()
            .registerModule(new JavaTimeModule());

    record EventSeed(String calendarId, String summary, String description,
                     String location, io.casehub.connectors.calendar.spi.EventTiming timing,
                     List<String> attendees) {}

    static List<CalendarInfo> loadCalendars() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/calendars.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/calendars.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<EventSeed> loadEvents() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/events.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/events.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
