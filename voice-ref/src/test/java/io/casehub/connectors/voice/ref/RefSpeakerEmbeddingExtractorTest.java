package io.casehub.connectors.voice.ref;

import io.casehub.connectors.voice.model.SpeakerEmbedding;
import io.casehub.connectors.voice.model.SpeakerMatch;
import io.casehub.connectors.voice.model.SynthesisOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class RefSpeakerEmbeddingExtractorTest {

    private final RefSpeakerEmbeddingExtractor extractor = new RefSpeakerEmbeddingExtractor();
    private final RefSpeakerRegistry registry = new RefSpeakerRegistry();

    @Test
    void differentVoiceIdsProduceDistinctEmbeddings() {
        SpeakerEmbedding alice = RefSpeakerEmbeddingExtractor.embeddingFor("af_heart");
        SpeakerEmbedding bob = RefSpeakerEmbeddingExtractor.embeddingFor("bm_daniel");
        SpeakerEmbedding unknown = RefSpeakerEmbeddingExtractor.embeddingFor("unknown");

        assertThat(cosine(alice.vector(), bob.vector())).isLessThan(0.9);
        assertThat(cosine(alice.vector(), unknown.vector())).isLessThan(0.9);
        assertThat(cosine(bob.vector(), unknown.vector())).isLessThan(0.9);
    }

    @Test
    void sameVoiceIdProducesIdenticalEmbeddings() {
        SpeakerEmbedding first = RefSpeakerEmbeddingExtractor.embeddingFor("af_heart");
        SpeakerEmbedding second = RefSpeakerEmbeddingExtractor.embeddingFor("af_heart");

        assertThat(cosine(first.vector(), second.vector())).isCloseTo(1.0, offset(1e-6));
    }

    @Test
    void registryDiscriminatesBetweenSpeakers() {
        registry.register("Alice Smith", RefSpeakerEmbeddingExtractor.embeddingFor("af_heart"));
        registry.register("Bob Smith", RefSpeakerEmbeddingExtractor.embeddingFor("bm_daniel"));

        SpeakerMatch aliceMatch = registry.identify(
                RefSpeakerEmbeddingExtractor.embeddingFor("af_heart"), 0.5).orElseThrow();
        assertThat(aliceMatch.name()).isEqualTo("Alice Smith");

        SpeakerMatch bobMatch = registry.identify(
                RefSpeakerEmbeddingExtractor.embeddingFor("bm_daniel"), 0.5).orElseThrow();
        assertThat(bobMatch.name()).isEqualTo("Bob Smith");
    }

    @Test
    void registryDiscriminatesViaTtsWavRoundTrip() {
        var tts = new ReferenceVoicePlatform().textToSpeech();

        registry.register("Alice Smith", extractFromWav(
                tts.synthesise("Hello, I am Alice", new SynthesisOptions("af_heart", "en", "wav", false)).audioData()));
        registry.register("Bob Smith", extractFromWav(
                tts.synthesise("Hello, I am Bob", new SynthesisOptions("bm_daniel", "en", "wav", false)).audioData()));

        SpeakerMatch aliceMatch = registry.identify(extractFromWav(
                tts.synthesise("I confirm", new SynthesisOptions("af_heart", "en", "wav", false)).audioData()), 0.5).orElseThrow();
        assertThat(aliceMatch.name()).isEqualTo("Alice Smith");

        SpeakerMatch bobMatch = registry.identify(extractFromWav(
                tts.synthesise("I confirm", new SynthesisOptions("bm_daniel", "en", "wav", false)).audioData()), 0.5).orElseThrow();
        assertThat(bobMatch.name()).isEqualTo("Bob Smith");
    }

    private SpeakerEmbedding extractFromWav(byte[] wav) {
        int dataOffset = 44;
        int sampleCount = (wav.length - dataOffset) / 2;
        float[] samples = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int idx = dataOffset + i * 2;
            short s = (short) ((wav[idx + 1] << 8) | (wav[idx] & 0xFF));
            samples[i] = s / (float) Short.MAX_VALUE;
        }
        return extractor.extract(samples, 16000);
    }

    private static double cosine(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }
}
