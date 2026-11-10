package io.casehub.connectors.voice.spi;

import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.TranscriptionOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpVoicePlatformTest {

    private final VoicePlatform platform = new NoOpVoicePlatform();

    @Test
    void idIsNone() {
        assertThat(platform.id()).isEqualTo("none");
    }

    @Test
    void supportsReturnsFalseForAll() {
        assertThat(platform.supports(SpeechToText.class)).isFalse();
        assertThat(platform.supports(TextToSpeech.class)).isFalse();
    }

    @Test
    void speechToTextThrows() {
        assertThatThrownBy(() -> platform.speechToText()
                .transcribe(new byte[0], TranscriptionOptions.defaults()))
                .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void textToSpeechThrows() {
        assertThatThrownBy(() -> platform.textToSpeech()
                .synthesise("hello", new SynthesisOptions(null, "en", null, false)))
                .isInstanceOf(UnsupportedCapabilityException.class);
    }
}
