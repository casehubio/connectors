package io.casehub.connectors.voice.config;

import java.util.Map;
import java.util.stream.Collectors;

public record VoiceManifestResult(
    Map<String, VoiceAliasDeclaration> aliases,
    VoiceManifestDefaults defaults,
    Map<String, Map<String, String>> providers
) {
    public VoiceManifestResult {
        aliases = aliases != null ? Map.copyOf(aliases) : Map.of();
        providers = providers != null
            ? providers.entrySet().stream().collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, e -> Map.copyOf(e.getValue())))
            : Map.of();
    }

    public static VoiceManifestResult empty() {
        return new VoiceManifestResult(Map.of(), null, Map.of());
    }
}
