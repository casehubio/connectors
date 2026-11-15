package io.casehub.connectors.calendar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpCalendarPlatformTest {

    private final NoOpCalendarPlatform noop = new NoOpCalendarPlatform();
    private final io.casehub.connectors.calendar.spi.CalendarPlatform.CalendarOperations ops = noop.calendarOps("test-user");

    @Test
    void id_returnsNone() {
        assertThat(noop.id()).isEqualTo("none");
    }

    @Test
    void calendarOpsReturnsOperations() {
        assertThat(noop.calendarOps("any-user")).isNotNull();
    }

    @Test
    void listCalendars_returnsEmptyList() {
        assertThat(ops.listCalendars()).isEmpty();
    }

    @Test
    void listEvents_returnsEmptyList() {
        assertThat(ops.listEvents("cal-1", null, null)).isEmpty();
    }

    @Test
    void getEvent_throwsUnsupported() {
        assertThatThrownBy(() -> ops.getEvent("cal-1", "evt-1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void createEvent_throwsUnsupported() {
        assertThatThrownBy(() -> ops.createEvent("cal-1", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void updateEvent_throwsUnsupported() {
        assertThatThrownBy(() -> ops.updateEvent("cal-1", "evt-1", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deleteEvent_throwsUnsupported() {
        assertThatThrownBy(() -> ops.deleteEvent("cal-1", "evt-1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listEventsSync_throwsUnsupported() {
        assertThatThrownBy(() -> ops.listEventsSync("cal-1", io.casehub.connectors.SyncRequest.initial(100)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

}
