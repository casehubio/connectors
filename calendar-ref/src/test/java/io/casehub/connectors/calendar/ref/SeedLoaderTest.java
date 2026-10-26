package io.casehub.connectors.calendar.ref;

import io.casehub.connectors.calendar.spi.EventTiming;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadCalendars_returns3Calendars() {
        var calendars = SeedLoader.loadCalendars();
        assertThat(calendars).hasSize(3);
        assertThat(calendars).extracting("id")
                .containsExactly("cal-jonny", "cal-sarah", "cal-family");
    }

    @Test
    void loadCalendars_jonnyIsPrimary() {
        var calendars = SeedLoader.loadCalendars();
        var primary = calendars.stream().filter(c -> c.primary()).findFirst().orElseThrow();
        assertThat(primary.id()).isEqualTo("cal-jonny");
    }

    @Test
    void loadEvents_returnsEventsAcrossAllCalendars() {
        var events = SeedLoader.loadEvents();
        assertThat(events).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    void loadEvents_coversAllThreeCalendars() {
        var events = SeedLoader.loadEvents();
        var calendarIds = events.stream().map(SeedLoader.EventSeed::calendarId).distinct().toList();
        assertThat(calendarIds).contains("cal-jonny", "cal-sarah", "cal-family");
    }

    @Test
    void loadEvents_parsesTimedEvents() {
        var events = SeedLoader.loadEvents();
        var timed = events.stream()
                .filter(e -> e.timing() instanceof EventTiming.Timed)
                .toList();
        assertThat(timed).isNotEmpty();
        var first = (EventTiming.Timed) timed.getFirst().timing();
        assertThat(first.start()).isNotNull();
        assertThat(first.end()).isNotNull();
        assertThat(first.timeZone()).isNotNull();
    }

    @Test
    void loadEvents_parsesAllDayEvents() {
        var events = SeedLoader.loadEvents();
        var allDay = events.stream()
                .filter(e -> e.timing() instanceof EventTiming.AllDay)
                .toList();
        assertThat(allDay).isNotEmpty();
        var first = (EventTiming.AllDay) allDay.getFirst().timing();
        assertThat(first.start()).isNotNull();
        assertThat(first.end()).isNotNull();
    }

    @Test
    void loadEvents_jonnyCalendarHasWorkAndGym() {
        var events = SeedLoader.loadEvents();
        var jonnyEvents = events.stream()
                .filter(e -> "cal-jonny".equals(e.calendarId()))
                .toList();
        assertThat(jonnyEvents).extracting("summary")
                .anyMatch(s -> ((String) s).contains("Meridian Engineering"));
        assertThat(jonnyEvents).extracting("summary")
                .anyMatch(s -> ((String) s).contains("PureGym"));
    }

    @Test
    void loadEvents_familyCalendarHasBirthdaysAndHolidays() {
        var events = SeedLoader.loadEvents();
        var familyEvents = events.stream()
                .filter(e -> "cal-family".equals(e.calendarId()))
                .toList();
        assertThat(familyEvents).extracting("summary")
                .anyMatch(s -> ((String) s).contains("birthday"));
        assertThat(familyEvents).extracting("summary")
                .anyMatch(s -> ((String) s).contains("Algarve"));
    }
}
