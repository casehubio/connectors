package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.VoiceDescriptor;
import io.casehub.connectors.voice.model.VoiceQuery;
import java.util.List;
import java.util.Optional;

public interface VoiceRegistry {
    Optional<VoiceDescriptor> resolveById(String voiceId);
    List<VoiceDescriptor> query(VoiceQuery query);
    List<VoiceDescriptor> all();

    static VoiceRegistry empty() {
        return new VoiceRegistry() {
            @Override public Optional<VoiceDescriptor> resolveById(String id) { return Optional.empty(); }
            @Override public List<VoiceDescriptor> query(VoiceQuery q) { return List.of(); }
            @Override public List<VoiceDescriptor> all() { return List.of(); }
        };
    }
}
