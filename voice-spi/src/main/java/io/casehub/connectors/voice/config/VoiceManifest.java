package io.casehub.connectors.voice.config;

import io.casehub.connectors.voice.model.VoiceDescriptor;
import java.util.List;
import java.util.Map;

public record VoiceManifest(
    List<VoiceProviderDeclaration> providers,
    Map<String, VoiceDescriptor> voices,
    Map<String, VoiceAliasDeclaration> aliases,
    VoiceManifestDefaults defaults
) {
    public VoiceManifest {
        providers = providers != null ? List.copyOf(providers) : List.of();
        voices = voices != null ? Map.copyOf(voices) : Map.of();
        aliases = aliases != null ? Map.copyOf(aliases) : Map.of();
    }
}
