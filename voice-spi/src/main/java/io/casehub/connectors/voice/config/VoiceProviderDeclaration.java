package io.casehub.connectors.voice.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VoiceProviderDeclaration(
    String vendor,
    String credential,
    @JsonProperty("model-dir") String modelDir
) {}
