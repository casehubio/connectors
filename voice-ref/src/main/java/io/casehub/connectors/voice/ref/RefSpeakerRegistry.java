package io.casehub.connectors.voice.ref;

import io.casehub.connectors.voice.model.SpeakerEmbedding;
import io.casehub.connectors.voice.model.SpeakerMatch;
import io.casehub.connectors.voice.spi.SpeakerRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RefSpeakerRegistry implements SpeakerRegistry {

    private final Map<String, SpeakerEmbedding> speakers = new ConcurrentHashMap<>();

    @Override
    public void register(String name, SpeakerEmbedding embedding) {
        speakers.put(name, embedding);
    }

    @Override
    public Optional<SpeakerMatch> identify(SpeakerEmbedding embedding, double confidenceThreshold) {
        String bestName = null;
        double bestScore = 0.0;
        for (var entry : speakers.entrySet()) {
            double score = cosineSimilarity(embedding.vector(), entry.getValue().vector());
            if (score > bestScore) {
                bestScore = score;
                bestName = entry.getKey();
            }
        }
        if (bestName != null && bestScore >= confidenceThreshold) {
            return Optional.of(new SpeakerMatch(bestName, bestScore));
        }
        return Optional.empty();
    }

    @Override
    public List<String> registeredSpeakers() {
        return List.copyOf(speakers.keySet());
    }

    @Override
    public void remove(String name) {
        speakers.remove(name);
    }

    private static double cosineSimilarity(float[] a, float[] b) {
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
