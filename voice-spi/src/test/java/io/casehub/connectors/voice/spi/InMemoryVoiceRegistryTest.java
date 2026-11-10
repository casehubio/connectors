package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.VoiceCapability;
import io.casehub.connectors.voice.model.VoiceDescriptor;
import io.casehub.connectors.voice.model.VoiceQuery;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryVoiceRegistryTest {

    static final VoiceDescriptor HEART = new VoiceDescriptor(
        "af_heart", "kokoro", "0", "Heart", "female", "american",
        4.5, List.of("en-US"), null, null, null, null, VoiceCapability.TTS, Map.of());

    static final VoiceDescriptor DANIEL = new VoiceDescriptor(
        "bm_daniel", "kokoro", "4", "Daniel", "male", "british",
        4.3, List.of("en-GB"), null, null, null, null, VoiceCapability.TTS, Map.of());

    @Test
    void emptyRegistryResolvesNothing() {
        var reg = new InMemoryVoiceRegistry();
        assertThat(reg.resolveById("af_heart")).isEmpty();
        assertThat(reg.all()).isEmpty();
    }

    @Test
    void replaceSourcePopulatesRegistry() {
        var reg = new InMemoryVoiceRegistry();
        var delta = reg.replaceSource("manifest", 0, List.of(HEART, DANIEL));
        assertThat(delta.addedIds()).containsExactlyInAnyOrder("af_heart", "bm_daniel");
        assertThat(reg.resolveById("af_heart")).contains(HEART);
        assertThat(reg.all()).hasSize(2);
    }

    @Test
    void replaceSourceReplacesExisting() {
        var reg = new InMemoryVoiceRegistry();
        reg.replaceSource("s1", 0, List.of(HEART));
        var updated = new VoiceDescriptor(
            "af_heart", "openai", "nova", "Nova", "female", "american",
            4.8, List.of("en-US"), null, null, null, null, VoiceCapability.TTS, Map.of());
        reg.replaceSource("s1", 0, List.of(updated));
        assertThat(reg.resolveById("af_heart").get().vendor()).isEqualTo("openai");
    }

    @Test
    void queryFilters() {
        var reg = new InMemoryVoiceRegistry();
        reg.replaceSource("m", 0, List.of(HEART, DANIEL));
        assertThat(reg.query(VoiceQuery.builder().gender("female").build()))
            .containsExactly(HEART);
        assertThat(reg.query(VoiceQuery.builder().accent("british").build()))
            .containsExactly(DANIEL);
        assertThat(reg.query(VoiceQuery.all())).hasSize(2);
    }

    @Test
    void deltaTracksRemovals() {
        var reg = new InMemoryVoiceRegistry();
        reg.replaceSource("s1", 0, List.of(HEART, DANIEL));
        var delta = reg.replaceSource("s1", 0, List.of(HEART));
        assertThat(delta.removedIds()).containsExactly("bm_daniel");
    }
}
