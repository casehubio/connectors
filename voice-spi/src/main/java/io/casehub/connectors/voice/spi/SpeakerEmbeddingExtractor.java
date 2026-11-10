package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.SpeakerEmbedding;

public interface SpeakerEmbeddingExtractor {
    SpeakerEmbedding extract(float[] samples, int sampleRate);
}
