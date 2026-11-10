package io.casehub.connectors.voice.sherpa;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
interface TtsVoiceEncoder {
    VoiceData encode(byte[] audioData, @Nullable String transcriptText);
}
