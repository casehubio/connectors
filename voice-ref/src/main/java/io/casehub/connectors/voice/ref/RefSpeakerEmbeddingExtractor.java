package io.casehub.connectors.voice.ref;

import io.casehub.connectors.voice.model.SpeakerEmbedding;
import io.casehub.connectors.voice.spi.SpeakerEmbeddingExtractor;

import java.nio.charset.StandardCharsets;

public class RefSpeakerEmbeddingExtractor implements SpeakerEmbeddingExtractor {

    @Override
    public SpeakerEmbedding extract(float[] samples, int sampleRate) {
        String voiceId = extractVoiceTag(samples);
        return embeddingFor(voiceId);
    }

    private static SpeakerEmbedding embeddingFor(String voiceId) {
        float[] vector = new float[8];
        int     hash   = voiceId.hashCode();
        for (int i = 0; i < vector.length; i++) {
            vector[i] = ((hash >> (i * 4)) & 0xF) / 15.0f;
        }
        return new SpeakerEmbedding(vector, vector.length);
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
