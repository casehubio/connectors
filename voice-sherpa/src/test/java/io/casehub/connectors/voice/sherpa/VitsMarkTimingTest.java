package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.model.MarkTiming;
import io.casehub.connectors.voice.model.TextMark;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VitsMarkTimingTest {

    @Test
    void countIpaWords_emptyString() {
        assertThat(VitsTextToSpeech.countIpaWords("")).isZero();
    }

    @Test
    void countIpaWords_null() {
        assertThat(VitsTextToSpeech.countIpaWords(null)).isZero();
    }

    @Test
    void countIpaWords_singleWord() {
        assertThat(VitsTextToSpeech.countIpaWords("hɛˈloʊ")).isEqualTo(1);
    }

    @Test
    void countIpaWords_twoWords() {
        assertThat(VitsTextToSpeech.countIpaWords("hɛˈloʊ wɜːld")).isEqualTo(2);
    }

    @Test
    void countIpaWords_multipleSpaces() {
        assertThat(VitsTextToSpeech.countIpaWords("hɛˈloʊ  wɜːld")).isEqualTo(2);
    }

    @Test
    void computeWordOnsets_twoWords() {
        // Simulate: pad BOS pad p1 pad p2 pad SPACE pad p3 pad p4 pad EOS pad
        // IDs:      0   1   0  10  0  11  0   20   0  12  0  13  0   2   0
        int padId = 0, bosId = 1, eosId = 2, spaceId = 20;
        List<Integer> ids = List.of(0, 1, 0, 10, 0, 11, 0, 20, 0, 12, 0, 13, 0, 2, 0);
        // All durations are 1.0 frame each for simplicity
        float[] dur = new float[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        int hopLength = 256;
        int sampleRate = 22050;
        double msPerFrame = hopLength * 1000.0 / sampleRate;

        var result = VitsTextToSpeech.computeWordOnsets(ids, dur, padId, bosId, eosId, spaceId, hopLength, sampleRate);

        assertThat(result.wordOnsets()).hasSize(2);
        // Word 0 onset: after pad(0)+BOS(1)+pad(2) = 3 frames
        assertThat(result.wordOnsets().get(0)).isEqualTo(Math.round(3 * msPerFrame));
        // Word 1 onset: after pad+BOS+pad+p1+pad+p2+pad+SPACE+pad = 9 frames
        assertThat(result.wordOnsets().get(1)).isEqualTo(Math.round(9 * msPerFrame));
        assertThat(result.spaceTokenCount()).isEqualTo(1);
    }

    @Test
    void computeWordOnsets_singleWord() {
        List<Integer> ids = List.of(0, 1, 0, 10, 0, 11, 0, 2, 0);
        float[] dur = new float[]{1, 1, 1, 1, 1, 1, 1, 1, 1};
        int padId = 0, bosId = 1, eosId = 2, spaceId = 20;

        var result = VitsTextToSpeech.computeWordOnsets(ids, dur, padId, bosId, eosId, spaceId, 256, 22050);

        assertThat(result.wordOnsets()).hasSize(1);
        assertThat(result.spaceTokenCount()).isZero();
    }

    @Test
    void resolveMarkTimings_consistentGuard_returnTimings() {
        // 2 words, 1 space token
        // wordOnsets: [0, 100]
        var wordOnsets = new VitsTextToSpeech.WordOnsetResult(List.of(0L, 100L), 1);

        // prefix "hello " → 1 word, suffix "world" → 1 word
        // Guard: 1 + 1 == 1 + 1 ✓
        var marks = List.of(
                new TextMark("start", 0),
                new TextMark("world", 6));

        // prefixWordCounts: [0, 1] (empty prefix = 0 words, "hello " = 1 word)
        List<Integer> prefixCounts = List.of(0, 1);
        // suffixWordCounts: [2, 1]
        List<Integer> suffixCounts = List.of(2, 1);

        var result = VitsTextToSpeech.resolveMarkTimings(marks, wordOnsets, prefixCounts, suffixCounts);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new MarkTiming("start", 0));
        assertThat(result.get(1)).isEqualTo(new MarkTiming("world", 100));
    }

    @Test
    void resolveMarkTimings_inconsistentGuard_returnsEmpty() {
        var wordOnsets = new VitsTextToSpeech.WordOnsetResult(List.of(0L, 100L), 1);
        var marks = List.of(new TextMark("bad", 3));

        // Mid-word split: prefix has 1 word, suffix has 2 words
        // Guard: 1 + 2 = 3 ≠ 1 + 1 = 2 → fail
        List<Integer> prefixCounts = List.of(1);
        List<Integer> suffixCounts = List.of(2);

        var result = VitsTextToSpeech.resolveMarkTimings(marks, wordOnsets, prefixCounts, suffixCounts);

        assertThat(result).isEmpty();
    }

    @Test
    void resolveMarkTimings_nonMonotonic_returnsEmpty() {
        var wordOnsets = new VitsTextToSpeech.WordOnsetResult(List.of(0L, 100L, 200L), 2);
        var marks = List.of(
                new TextMark("second", 6),
                new TextMark("first", 0));

        // Both pass the consistency guard but offsets are non-monotonic (100, 0)
        List<Integer> prefixCounts = List.of(1, 0);
        List<Integer> suffixCounts = List.of(2, 3);

        var result = VitsTextToSpeech.resolveMarkTimings(marks, wordOnsets, prefixCounts, suffixCounts);

        assertThat(result).isEmpty();
    }
}
