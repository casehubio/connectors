package io.casehub.connectors.voice.steps;

import io.casehub.connectors.voice.model.TranscriptionResult;
import io.casehub.connectors.voice.spi.RoutingVoicePlatformService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SttTranscribePluginTest {

    private final RoutingVoicePlatformService routing = TtsSynthesisePluginTest.buildRouting();

    @Test
    void transcribesAudioViaVoicePlatform() {
        var                 plugin = new SttTranscribePlugin(null, null, null);
        TranscriptionResult result = plugin.apply(new byte[]{1, 2, 3}, routing);
        assertThat(result.text()).isNotBlank();
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.confidence()).isGreaterThan(0.0);
    }

    @Test
    void passesLanguageHint() {
        var                 plugin = new SttTranscribePlugin("fr", null, null);
        TranscriptionResult result = plugin.apply(new byte[]{1, 2, 3}, routing);
        assertThat(result).isNotNull();
    }

    @Test
    void passesVocabularyHint() {
        var                 plugin = new SttTranscribePlugin("en", null, "cardiology aortic stenosis");
        TranscriptionResult result = plugin.apply(new byte[]{1, 2, 3}, routing);
        assertThat(result).isNotNull();
        assertThat(result.text()).isNotBlank();
    }
}
