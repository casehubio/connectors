package io.casehub.connectors.voice.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceQueryTest {

    @Test
    void allReturnsAllNulls() {
        var query = VoiceQuery.all();
        assertThat(query.vendor()).isNull();
        assertThat(query.gender()).isNull();
        assertThat(query.accent()).isNull();
        assertThat(query.minQuality()).isNull();
        assertThat(query.language()).isNull();
        assertThat(query.style()).isNull();
        assertThat(query.capability()).isNull();
        assertThat(query.preferVendor()).isNull();
    }

    @Test
    void builderSetsAllFields() {
        var query = VoiceQuery.builder()
                              .vendor("kokoro")
                              .gender("female")
                              .accent("american")
                              .minQuality(4.0)
                              .language("en-US")
                              .style("newscast")
                              .capability(VoiceCapability.TTS)
                              .preferVendor("kokoro")
                              .build();
        assertThat(query.vendor()).isEqualTo("kokoro");
        assertThat(query.gender()).isEqualTo("female");
        assertThat(query.accent()).isEqualTo("american");
        assertThat(query.minQuality()).isEqualTo(4.0);
        assertThat(query.language()).isEqualTo("en-US");
        assertThat(query.style()).isEqualTo("newscast");
        assertThat(query.capability()).isEqualTo(VoiceCapability.TTS);
        assertThat(query.preferVendor()).isEqualTo("kokoro");
    }

    @Test
    void builderPartialFieldsLeavesOthersNull() {
        var query = VoiceQuery.builder()
                              .gender("male")
                              .minQuality(3.5)
                              .build();
        assertThat(query.gender()).isEqualTo("male");
        assertThat(query.minQuality()).isEqualTo(3.5);
        assertThat(query.vendor()).isNull();
        assertThat(query.accent()).isNull();
        assertThat(query.language()).isNull();
        assertThat(query.capability()).isNull();
    }

    @Test
    void recordEquality() {
        var q1 = VoiceQuery.builder().gender("female").capability(VoiceCapability.TTS).build();
        var q2 = VoiceQuery.builder().gender("female").capability(VoiceCapability.TTS).build();
        assertThat(q1).isEqualTo(q2);
    }
}
