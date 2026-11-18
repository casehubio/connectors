package io.casehub.connectors.voice.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkTimingTest {

    @Test
    void createsWithValidFields() {
        var timing = new MarkTiming("word1", 150);
        assertThat(timing.id()).isEqualTo("word1");
        assertThat(timing.offsetMs()).isEqualTo(150);
    }

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> new MarkTiming(null, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNegativeOffsetMs() {
        assertThatThrownBy(() -> new MarkTiming("m1", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsZeroOffsetMs() {
        var timing = new MarkTiming("start", 0);
        assertThat(timing.offsetMs()).isZero();
    }
}
