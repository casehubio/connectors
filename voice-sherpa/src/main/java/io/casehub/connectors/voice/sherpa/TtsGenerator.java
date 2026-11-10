package io.casehub.connectors.voice.sherpa;

import org.jspecify.annotations.Nullable;

interface TtsGenerator {
    GeneratorOutput generate(int[] tokens, @Nullable VoiceData voiceData,
                             GeneratorConfig config);
}
