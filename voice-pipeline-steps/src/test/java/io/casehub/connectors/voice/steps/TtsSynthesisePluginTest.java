package io.casehub.connectors.voice.steps;

import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.ref.ReferenceVoicePlatform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TtsSynthesisePluginTest {

    @Test
    void synthesisesTextViaVoicePlatform() {
        var platform = new ReferenceVoicePlatform();
        var plugin = new TtsSynthesisePlugin(null, "en");
        SynthesisResult result = plugin.apply("Hello world", platform);
        assertThat(result.audioData()).isNotEmpty();
        assertThat(result.audioFormat()).isEqualTo("wav");
        assertThat(result.audioData()[0]).isEqualTo((byte) 'R');
    }

    @Test
    void passesVoiceAndLanguage() {
        var platform = new ReferenceVoicePlatform();
        var plugin = new TtsSynthesisePlugin("default", "fr");
        SynthesisResult result = plugin.apply("Bonjour", platform);
        assertThat(result).isNotNull();
        assertThat(result.audioData()).isNotEmpty();
    }
}
