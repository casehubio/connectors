package io.casehub.connectors.calendar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpCalendarPlatformTest {

    private final NoOpCalendarPlatform noop = new NoOpCalendarPlatform();

    @Test
    void id_returnsNone() {
        assertThat(noop.id()).isEqualTo("none");
    }

    @Test
    void listCalendars_returnsEmptyList() {
        assertThat(noop.listCalendars()).isEmpty();
    }

    @Test
    void listEvents_returnsEmptyList() {
        assertThat(noop.listEvents("cal-1", null, null)).isEmpty();
    }

    @Test
    void getEvent_throwsUnsupported() {
        assertThatThrownBy(() -> noop.getEvent("cal-1", "evt-1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void createEvent_throwsUnsupported() {
        assertThatThrownBy(() -> noop.createEvent("cal-1", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void updateEvent_throwsUnsupported() {
        assertThatThrownBy(() -> noop.updateEvent("cal-1", "evt-1", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deleteEvent_throwsUnsupported() {
        assertThatThrownBy(() -> noop.deleteEvent("cal-1", "evt-1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
