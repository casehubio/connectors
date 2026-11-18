package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.MarkTiming;
import io.casehub.connectors.voice.model.PhonemeTiming;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.model.TextMark;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkTimingEnricherTest {

    private static final List<TextMark> MARKS = List.of(
            new TextMark("hello", 0),
            new TextMark("world", 6));

    private static byte[] wavWithDuration(int sampleRate, int samples) {
        int bitsPerSample = 16;
        int channels = 1;
        int dataSize = samples * (bitsPerSample / 8) * channels;
        int byteRate = sampleRate * channels * (bitsPerSample / 8);
        byte[] wav = new byte[44 + dataSize];
        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        writeInt32LE(wav, 4, 36 + dataSize);
        wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
        wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
        writeInt32LE(wav, 16, 16);
        writeInt16LE(wav, 20, 1);
        writeInt16LE(wav, 22, channels);
        writeInt32LE(wav, 24, sampleRate);
        writeInt32LE(wav, 28, byteRate);
        writeInt16LE(wav, 32, channels * (bitsPerSample / 8));
        writeInt16LE(wav, 34, bitsPerSample);
        wav[36] = 'd'; wav[37] = 'a'; wav[38] = 't'; wav[39] = 'a';
        writeInt32LE(wav, 40, dataSize);
        return wav;
    }

    private static void writeInt16LE(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
    }

    private static void writeInt32LE(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) (value >> 16);
        data[offset + 3] = (byte) (value >> 24);
    }

    @Test
    void passesThroughWhenNoMarksRequested() {
        TextToSpeech delegate = (text, opts) ->
                new SynthesisResult(new byte[]{1}, "wav");

        var enriched = MarkTimingEnricher.wrap(delegate);
        var result = enriched.synthesise("hello world", SynthesisOptions.defaults());

        assertThat(result.marks()).isEmpty();
    }

    @Test
    void passesThroughWhenDelegateReturnsMarks() {
        var nativeMarks = List.of(new MarkTiming("hello", 0), new MarkTiming("world", 500));
        TextToSpeech delegate = (text, opts) ->
                new SynthesisResult(new byte[]{1}, "wav", List.of(), nativeMarks);

        var opts = new SynthesisOptions(null, null, "wav", false, MARKS);
        var enriched = MarkTimingEnricher.wrap(delegate);
        var result = enriched.synthesise("hello world", opts);

        assertThat(result.marks()).isEqualTo(nativeMarks);
    }

    @Test
    void phonemeProportionalFallbackUsesPhonemeTimings() {
        var phonemes = List.of(
                new PhonemeTiming("h", 0, 100),
                new PhonemeTiming("eh", 100, 200),
                new PhonemeTiming("l", 200, 350),
                new PhonemeTiming("ow", 350, 500),
                new PhonemeTiming("w", 500, 600),
                new PhonemeTiming("er", 600, 750),
                new PhonemeTiming("l", 750, 850),
                new PhonemeTiming("d", 850, 1000));
        TextToSpeech delegate = (text, opts) ->
                new SynthesisResult(new byte[]{1}, "wav", phonemes);

        var opts = new SynthesisOptions(null, null, "wav", false, MARKS);
        var enriched = MarkTimingEnricher.wrap(delegate);
        var result = enriched.synthesise("hello world", opts);

        assertThat(result.marks()).hasSize(2);
        assertThat(result.marks().get(0).id()).isEqualTo("hello");
        assertThat(result.marks().get(0).offsetMs()).isEqualTo(0);
        assertThat(result.marks().get(1).id()).isEqualTo("world");
        assertThat(result.marks().get(1).offsetMs()).isGreaterThan(0);
    }

    @Test
    void characterProportionalFallbackUsesWavDuration() {
        byte[] wav = wavWithDuration(22050, 22050);
        TextToSpeech delegate = (text, opts) ->
                new SynthesisResult(wav, "wav");

        var opts = new SynthesisOptions(null, null, "wav", false, MARKS);
        var enriched = MarkTimingEnricher.wrap(delegate, 22050);
        var result = enriched.synthesise("hello world", opts);

        assertThat(result.marks()).hasSize(2);
        assertThat(result.marks().get(0).id()).isEqualTo("hello");
        assertThat(result.marks().get(0).offsetMs()).isEqualTo(0);
        assertThat(result.marks().get(1).id()).isEqualTo("world");
        long expectedMs = (long) (6.0 / 11 * 1000);
        assertThat(result.marks().get(1).offsetMs()).isEqualTo(expectedMs);
    }

    @Test
    void characterProportionalReturnsEmptyForTooShortWav() {
        TextToSpeech delegate = (text, opts) ->
                new SynthesisResult(new byte[10], "wav");

        var opts = new SynthesisOptions(null, null, "wav", false, MARKS);
        var enriched = MarkTimingEnricher.wrap(delegate);
        var result = enriched.synthesise("hello world", opts);

        assertThat(result.marks()).isEmpty();
    }

    @Test
    void preservesPhonemesAndAudioData() {
        var phonemes = List.of(new PhonemeTiming("ah", 0, 500));
        byte[] audio = wavWithDuration(22050, 22050);
        TextToSpeech delegate = (text, opts) ->
                new SynthesisResult(audio, "wav", phonemes);

        var marks = List.of(new TextMark("mid", 5));
        var opts = new SynthesisOptions(null, null, "wav", false, marks);
        var enriched = MarkTimingEnricher.wrap(delegate);
        var result = enriched.synthesise("hello world", opts);

        assertThat(result.phonemes()).isEqualTo(phonemes);
        assertThat(result.audioFormat()).isEqualTo("wav");
        assertThat(result.marks()).hasSize(1);
    }

    @Test
    void wavDurationMsCalculatesCorrectly() {
        byte[] wav = wavWithDuration(16000, 16000);
        assertThat(MarkTimingEnricher.wavDurationMs(wav, 16000)).isEqualTo(1000);
    }

    @Test
    void wavDurationMsReturnsZeroForNonWav() {
        assertThat(MarkTimingEnricher.wavDurationMs(new byte[]{1, 2, 3}, 22050)).isZero();
    }

    @Test
    void phonemeProportionalSnapToPhonemeStart() {
        var phonemes = List.of(
                new PhonemeTiming("a", 0, 250),
                new PhonemeTiming("b", 250, 500),
                new PhonemeTiming("c", 500, 750),
                new PhonemeTiming("d", 750, 1000));
        var marks = List.of(
                new TextMark("start", 0),
                new TextMark("mid", 5),
                new TextMark("end", 9));

        List<MarkTiming> timings = MarkTimingEnricher.phonemeProportional("0123456789", marks, phonemes);

        assertThat(timings).hasSize(3);
        assertThat(timings.get(0).offsetMs()).isEqualTo(0);
        assertThat(timings.get(1).offsetMs()).isEqualTo(500);
        assertThat(timings.get(2).offsetMs()).isEqualTo(750);
    }

    @Test
    void phonemeProportionalWithSingleCharText() {
        var              phonemes = List.of(new PhonemeTiming("a", 0, 500));
        var              marks    = List.of(new TextMark("only", 0));
        List<MarkTiming> timings  = MarkTimingEnricher.phonemeProportional("x", marks, phonemes);
        assertThat(timings).hasSize(1);
        assertThat(timings.getFirst().offsetMs()).isEqualTo(0);
    }

    @Test
    void phonemeProportionalWithMarkAtTextEnd() {
        var phonemes = List.of(
                new PhonemeTiming("a", 0, 250),
                new PhonemeTiming("b", 250, 500));
        var              marks   = List.of(new TextMark("end", 4));
        List<MarkTiming> timings = MarkTimingEnricher.phonemeProportional("abcd", marks, phonemes);
        assertThat(timings).hasSize(1);
        assertThat(timings.getFirst().offsetMs()).isEqualTo(250);
    }

    @Test
    void phonemeProportionalWithEmptyText() {
        var              phonemes = List.of(new PhonemeTiming("a", 0, 500));
        var              marks    = List.of(new TextMark("x", 0));
        List<MarkTiming> timings  = MarkTimingEnricher.phonemeProportional("", marks, phonemes);
        assertThat(timings).isEmpty();
    }

    @Test
    void phonemeProportionalClampsCharOffsetBeyondTextLength() {
        var phonemes = List.of(
                new PhonemeTiming("a", 0, 250),
                new PhonemeTiming("b", 250, 500));
        var              marks   = List.of(new TextMark("past", 10));
        List<MarkTiming> timings = MarkTimingEnricher.phonemeProportional("abcd", marks, phonemes);
        assertThat(timings).hasSize(1);
        assertThat(timings.getFirst().offsetMs()).isEqualTo(250);
    }

    @Test
    void characterProportionalWithSingleCharText() {
        byte[]           wav     = wavWithDuration(22050, 22050);
        var              marks   = List.of(new TextMark("only", 0));
        List<MarkTiming> timings = MarkTimingEnricher.characterProportional("x", marks, wav, 22050);
        assertThat(timings).hasSize(1);
        assertThat(timings.getFirst().offsetMs()).isEqualTo(0);
    }

    @Test
    void characterProportionalWithMarkAtTextEnd() {
        byte[]           wav     = wavWithDuration(22050, 22050);
        var              marks   = List.of(new TextMark("end", 4));
        List<MarkTiming> timings = MarkTimingEnricher.characterProportional("abcd", marks, wav, 22050);
        assertThat(timings).hasSize(1);
        assertThat(timings.getFirst().offsetMs()).isEqualTo(1000);
    }

    @Test
    void characterProportionalClampsCharOffsetBeyondTextLength() {
        byte[]           wav     = wavWithDuration(22050, 22050);
        var              marks   = List.of(new TextMark("past", 10));
        List<MarkTiming> timings = MarkTimingEnricher.characterProportional("abcd", marks, wav, 22050);
        assertThat(timings).hasSize(1);
        assertThat(timings.getFirst().offsetMs()).isEqualTo(1000);
    }
}
