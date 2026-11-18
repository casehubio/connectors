package io.casehub.connectors.voice.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextMarkTest {

    @Test
    void createsWithValidFields() {
        var mark = new TextMark("word1", 5);
        assertThat(mark.id()).isEqualTo("word1");
        assertThat(mark.charOffset()).isEqualTo(5);
    }

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> new TextMark(null, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNegativeCharOffset() {
        assertThatThrownBy(() -> new TextMark("m1", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsZeroCharOffset() {
        var mark = new TextMark("start", 0);
        assertThat(mark.charOffset()).isZero();
    }
}
