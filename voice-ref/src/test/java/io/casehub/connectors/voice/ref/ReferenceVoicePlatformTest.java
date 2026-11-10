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
                             .transcribe("hello world".getBytes(), TranscriptionOptions.defaults());
        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void synthesiseEncodesTextForRoundTrip() {
        var synthesised = platform.textToSpeech()
                .synthesise("hello world", new SynthesisOptions(null, "en", "wav", false));
        assertThat(synthesised.audioData()).isNotEmpty();
        assertThat(synthesised.audioFormat()).isEqualTo("wav");

        var transcribed = platform.speechToText()
                .transcribe(synthesised.audioData(), TranscriptionOptions.defaults());
        assertThat(transcribed.text()).isEqualTo("hello world");
    }
}
