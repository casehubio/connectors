package io.casehub.connectors.voice.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.casehub.connectors.voice.model.VoiceCapability;

public record VoiceAliasDeclaration(
    String voice,
    @JsonProperty("min-quality") Double minQuality,
    @JsonProperty("prefer-vendor") String preferVendor,
    String gender,
    String accent,
    String language,
    String style,
    VoiceCapability capability
) {}
