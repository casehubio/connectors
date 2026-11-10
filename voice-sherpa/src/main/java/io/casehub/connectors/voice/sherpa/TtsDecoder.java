package io.casehub.connectors.voice.sherpa;

import org.jspecify.annotations.Nullable;

interface TtsDecoder {
    float[] decode(GeneratorOutput generatorOutput, @Nullable VoiceData voiceData);
}
