package io.casehub.connectors.voice.ref;

import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceVoicePlatformTest {

    private final ReferenceVoicePlatform platform = new ReferenceVoicePlatform();

    @Test
    void idIsRef() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void supportsBothCapabilities() {
        assertThat(platform.supports(SpeechToText.class)).isTrue();
        assertThat(platform.supports(TextToSpeech.class)).isTrue();
    }

    @Test
    void transcribeReturnsCannedResult() {
        var result = platform.speechToText()
                .transcribe(new byte[]{1, 2, 3}, TranscriptionOptions.defaults());
        assertThat(result.text()).isNotBlank();
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void synthesiseReturnsSilentWav() {
        var result = platform.textToSpeech()
                .synthesise("hello", new SynthesisOptions(null, "en", "wav", false));
        assertThat(result.audioData()).isNotEmpty();
        assertThat(result.audioFormat()).isEqualTo("wav");
        assertThat(result.audioData()[0]).isEqualTo((byte) 'R');
        assertThat(result.audioData()[1]).isEqualTo((byte) 'I');
        assertThat(result.audioData()[2]).isEqualTo((byte) 'F');
        assertThat(result.audioData()[3]).isEqualTo((byte) 'F');
    }
}
