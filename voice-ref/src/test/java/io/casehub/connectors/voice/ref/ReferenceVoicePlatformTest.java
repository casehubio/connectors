package io.casehub.connectors.voice.ref;

import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.TextMark;
import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void synthesiseWithMarksReturnsCharacterProportionalTimings() {
        var marks = List.of(
                new TextMark("start", 0),
                new TextMark("mid", 5),
                new TextMark("end", 10)
                           );
        var options = new SynthesisOptions(null, "en", "wav", false, marks);
        var result  = platform.textToSpeech().synthesise("hello world", options);

        assertThat(result.marks()).hasSize(3);
        assertThat(result.marks().get(0).id()).isEqualTo("start");
        assertThat(result.marks().get(0).offsetMs()).isZero();
        assertThat(result.marks().get(1).id()).isEqualTo("mid");
        assertThat(result.marks().get(1).offsetMs()).isEqualTo(250);
        assertThat(result.marks().get(2).id()).isEqualTo("end");
        assertThat(result.marks().get(2).offsetMs()).isEqualTo(500);
    }

    @Test
    void synthesiseWithMarkBeyondTextLengthClampsToEnd() {
        var marks   = List.of(new TextMark("past", 20));
        var options = new SynthesisOptions(null, "en", "wav", false, marks);
        var result  = platform.textToSpeech().synthesise("hello world", options);

        long durationMs = 11 * 50L;
        assertThat(result.marks()).hasSize(1);
        assertThat(result.marks().getFirst().offsetMs()).isEqualTo(durationMs);
    }

    @Test
    void synthesiseWithoutMarksReturnsNoTimings() {
        var options = new SynthesisOptions(null, "en", "wav", false);
        var result  = platform.textToSpeech().synthesise("hello world", options);

        assertThat(result.marks()).isEmpty();
    }
}
