package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.SpeakerEmbedding;
import io.casehub.connectors.voice.model.SpeakerMatch;

import java.util.List;
import java.util.Optional;

public interface SpeakerRegistry {
    void register(String name, SpeakerEmbedding embedding);
    Optional<SpeakerMatch> identify(SpeakerEmbedding embedding, double confidenceThreshold);
    List<String> registeredSpeakers();
    void remove(String name);
}
