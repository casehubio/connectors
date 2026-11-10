package io.casehub.connectors.voice.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VoiceManifestDefaults(
    @JsonProperty("tts-vendor") String ttsVendor,
    @JsonProperty("stt-vendor") String sttVendor
) {}
