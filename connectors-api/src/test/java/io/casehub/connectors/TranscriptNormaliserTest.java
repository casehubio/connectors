package io.casehub.connectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptNormaliserTest {

    @Test
    void stripsDigitGroupingCommas() {
        assertThat(TranscriptNormaliser.normalise("7,429")).isEqualTo("7429");
    }

    @Test
    void collapsesHyphensAndSpaces() {
        assertThat(TranscriptNormaliser.normalise("TX-7429")).isEqualTo("tx 7429");
        assertThat(TranscriptNormaliser.normalise("TX 7,429")).isEqualTo("tx 7429");
    }

    @Test
    void containsNormalisedMatchesDespiteFormatting() {
        assertThat(TranscriptNormaliser.containsNormalised("TX 7,429", "TX-7429")).isTrue();
    }

    @Test
    void containsNormalisedMatchesSubstring() {
        assertThat(TranscriptNormaliser.containsNormalised(
                "Your reference is TX 7,429 and it has been confirmed", "TX-7429")).isTrue();
    }

    @Test
    void containsNormalisedRejectsNonMatch() {
        assertThat(TranscriptNormaliser.containsNormalised("TX 7,429", "TX-9999")).isFalse();
    }

    @Test
    void handlesNull() {
        assertThat(TranscriptNormaliser.normalise(null)).isEmpty();
        assertThat(TranscriptNormaliser.containsNormalised(null, "anything")).isFalse();
    }

    @Test
    void preservesNonGroupingCommas() {
        assertThat(TranscriptNormaliser.normalise("hello, world")).isEqualTo("hello, world");
    }
}
