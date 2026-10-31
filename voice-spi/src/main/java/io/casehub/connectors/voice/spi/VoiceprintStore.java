package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.SpeakerEmbedding;

import java.util.Map;

public interface VoiceprintStore {
    void save(String name, SpeakerEmbedding embedding);
    Map<String, SpeakerEmbedding> loadAll();
    void delete(String name);
}
