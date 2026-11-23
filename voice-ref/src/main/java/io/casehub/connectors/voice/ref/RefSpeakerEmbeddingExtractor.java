package io.casehub.connectors.voice.ref;

import io.casehub.connectors.voice.model.SpeakerEmbedding;
import io.casehub.connectors.voice.spi.SpeakerEmbeddingExtractor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RefSpeakerEmbeddingExtractor implements SpeakerEmbeddingExtractor {

    @Override
    public SpeakerEmbedding extract(float[] samples, int sampleRate) {
        String voiceId = extractVoiceTag(samples);
        return embeddingFor(voiceId);
    }

    private static final int DIMENSIONS = 16;

    static SpeakerEmbedding embeddingFor(String voiceId) {
        byte[] hash = sha256(voiceId);
        float[] vector = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = (hash[i] & 0xFF) / 127.5f - 1.0f;
        }
        return new SpeakerEmbedding(vector, DIMENSIONS);
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                                .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String extractVoiceTag(float[] samples) {
        byte[] bytes = new byte[Math.min(samples.length * 2, 256)];
        for (int i = 0; i < bytes.length / 2 && i < samples.length; i++) {
            short s = (short) (samples[i] * Short.MAX_VALUE);
            bytes[i * 2]     = (byte) s;
            bytes[i * 2 + 1] = (byte) (s >> 8);
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.contains("[voice:")) {
            int start = text.indexOf("[voice:") + 7;
            int end   = text.indexOf(']', start);
            if (end > start) {return text.substring(start, end);}
        }
        return "unknown";
    }
}
