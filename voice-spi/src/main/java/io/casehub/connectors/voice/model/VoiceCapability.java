package io.casehub.connectors.voice.model;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
public enum VoiceCapability {TTS, STT, BOTH}
