package io.casehub.connectors.calendar.spi;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EventTiming.Timed.class, name = "Timed"),
    @JsonSubTypes.Type(value = EventTiming.AllDay.class, name = "AllDay")
})
public sealed interface EventTiming {

    record Timed(Instant start, Instant end, ZoneId timeZone) implements EventTiming {
        public Timed {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(timeZone, "timeZone");
        }
    }

    record AllDay(LocalDate start, LocalDate end) implements EventTiming {
        public AllDay {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
        }
    }
}
