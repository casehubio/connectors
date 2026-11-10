package io.casehub.connectors.voice.config;

import io.casehub.connectors.voice.spi.MutableVoiceRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class VoiceManifestProcessor {

    private final MutableVoiceRegistry voiceRegistry;

    public VoiceManifestProcessor(MutableVoiceRegistry voiceRegistry) {
        this.voiceRegistry = voiceRegistry;
    }

    public VoiceManifestResult process(VoiceManifest manifest) {
        voiceRegistry.replaceSource("manifest", 0,
            new ArrayList<>(manifest.voices().values()));

        var providerProps = manifest.providers().stream()
            .collect(Collectors.toMap(
                VoiceProviderDeclaration::vendor,
                p -> {
                    var props = new LinkedHashMap<String, String>();
                    if (p.modelDir() != null) props.put("model-dir", p.modelDir());
                    if (p.credential() != null) props.put("credential", p.credential());
                    return Map.copyOf(props);
                }));

        return new VoiceManifestResult(manifest.aliases(), manifest.defaults(), providerProps);
    }
}
