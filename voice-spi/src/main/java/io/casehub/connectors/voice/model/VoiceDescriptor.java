package io.casehub.connectors.voice.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record VoiceDescriptor(
        String id,
        String vendor,
        @JsonProperty("voice-id") String voiceId,
        String name,
        String gender,
        String accent,
        double quality,
        List<String> languages,
        String style,
        @JsonProperty("sample-rate") Integer sampleRate,
        @JsonProperty("bit-depth") Integer bitDepth,
        Integer channels,
        VoiceCapability capability,
        Map<String, String> properties
) {
    public VoiceDescriptor {
        Objects.requireNonNull(vendor, "vendor");
        Objects.requireNonNull(voiceId, "voiceId");
        languages  = languages != null ? List.copyOf(languages) : List.of();
        properties = properties != null ? Map.copyOf(properties) : Map.of();
        if (capability == null) {capability = VoiceCapability.BOTH;}
    }
}
