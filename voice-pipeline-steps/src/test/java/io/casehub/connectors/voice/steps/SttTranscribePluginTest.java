package io.casehub.connectors.voice.steps;

import io.casehub.connectors.voice.model.TranscriptionResult;
import io.casehub.connectors.voice.ref.ReferenceVoicePlatform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SttTranscribePluginTest {

    @Test
    void transcribesAudioViaVoicePlatform() {
        var platform = new ReferenceVoicePlatform();
        var plugin = new SttTranscribePlugin(null, null);
        TranscriptionResult result = plugin.apply(new byte[]{1, 2, 3}, platform);
        assertThat(result.text()).isNotBlank();
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.confidence()).isGreaterThan(0.0);
    }

    @Test
    void passesLanguageHint() {
        var platform = new ReferenceVoicePlatform();
        var plugin = new SttTranscribePlugin("fr", null);
        TranscriptionResult result = plugin.apply(new byte[]{1, 2, 3}, platform);
        assertThat(result).isNotNull();
    }
}
