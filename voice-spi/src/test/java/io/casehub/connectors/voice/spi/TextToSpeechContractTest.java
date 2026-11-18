package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.PhonemeTiming;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TextToSpeechContractTest {

    @Test
    void synthesisResultNullAudioDataBecomesEmpty() {
        var result = new SynthesisResult(null, "wav", List.of());
        assertThat(result.audioData()).isEmpty();
    }

    @Test
    void synthesisResultRejectsNullFormat() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SynthesisResult(new byte[]{1}, null, List.of()));
    }

    @Test
    void synthesisResultDefensiveCopiesPhonemes() {
        final var phonemes = new java.util.ArrayList<>(List.of(
                new PhonemeTiming("ah", 0, 100)));
        final var result = new SynthesisResult(new byte[]{1}, "wav", phonemes);
        phonemes.clear();
        assertThat(result.phonemes()).hasSize(1);
    }

    @Test
    void synthesisResultNullPhonemesBecomesEmptyList() {
        final var result = new SynthesisResult(new byte[]{1}, "wav", null);
        assertThat(result.phonemes()).isEmpty();
    }

    @Test
    void synthesisResultTwoArgConstructor() {
        final var result = new SynthesisResult(new byte[]{1, 2}, "wav");
        assertThat(result.audioData()).containsExactly(1, 2);
        assertThat(result.audioFormat()).isEqualTo("wav");
        assertThat(result.phonemes()).isEmpty();
    }

    @Test
    void phonemeTimingRejectsNullPhoneme() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PhonemeTiming(null, 0, 100));
    }

    @Test
    void phonemeTimingRejectsInvalidRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PhonemeTiming("ah", 100, 50));
    }

    @Test
    void phonemeTimingAcceptsEqualStartEnd() {
        final var p = new PhonemeTiming("ah", 100, 100);
        assertThat(p.startMs()).isEqualTo(100);
        assertThat(p.endMs()).isEqualTo(100);
    }

    @Test
    void synthesisOptionsDefaultsReturnsWavNoPhonemes() {
        final var opts = SynthesisOptions.defaults();
        assertThat(opts.audioFormat()).isEqualTo("wav");
        assertThat(opts.includePhonemes()).isFalse();
        assertThat(opts.voice()).isNull();
        assertThat(opts.language()).isNull();
    }

    @Test
    void stubImplementationSynthesisesText() {
        final TextToSpeech tts = (text, options) ->
                new SynthesisResult(text.getBytes(), "wav", List.of());

        final var result = tts.synthesise("hello", SynthesisOptions.defaults());
        assertThat(result.audioData()).isEqualTo("hello".getBytes());
        assertThat(result.audioFormat()).isEqualTo("wav");
        assertThat(result.phonemes()).isEmpty();
    }

    @Test
    void synthesisResultTwoArgConstructorHasEmptyMarks() {
        final var result = new SynthesisResult(new byte[]{1}, "wav");
        assertThat(result.marks()).isEmpty();
    }

    @Test
    void synthesisResultThreeArgConstructorHasEmptyMarks() {
        final var result = new SynthesisResult(new byte[]{1}, "wav", List.of());
        assertThat(result.marks()).isEmpty();
    }

    @Test
    void synthesisResultDefensiveCopiesMarks() {
        final var marks = new java.util.ArrayList<>(List.of(
                new io.casehub.connectors.voice.model.MarkTiming("w1", 100)));
        final var result = new SynthesisResult(new byte[]{1}, "wav", List.of(), marks);
        marks.clear();
        assertThat(result.marks()).hasSize(1);
    }

    @Test
    void synthesisResultNullMarksBecomesEmptyList() {
        final var result = new SynthesisResult(new byte[]{1}, "wav", List.of(), null);
        assertThat(result.marks()).isEmpty();
    }

    @Test
    void synthesisOptionsDefaultsHasEmptyMarks() {
        assertThat(SynthesisOptions.defaults().marks()).isEmpty();
    }

    @Test
    void synthesisOptionsFourArgConstructorHasEmptyMarks() {
        final var opts = new SynthesisOptions("v1", "en", "wav", false);
        assertThat(opts.marks()).isEmpty();
    }

    @Test
    void synthesisOptionsDefensiveCopiesMarks() {
        final var marks = new java.util.ArrayList<>(List.of(
                new io.casehub.connectors.voice.model.TextMark("w1", 5)));
        final var opts = new SynthesisOptions("v1", "en", "wav", false, marks);
        marks.clear();
        assertThat(opts.marks()).hasSize(1);
    }

}
