package io.casehub.connectors.graphql.dto;

import io.casehub.connectors.voice.model.PhonemeTiming;
import java.util.List;

public record VoiceSynthesisResult(
        String audioBase64,
        String audioFormat,
        String voiceId,
        String voiceName,
        String vendor,
        List<PhonemeTiming> phonemes) {

    public VoiceSynthesisResult {
        phonemes = phonemes != null ? List.copyOf(phonemes) : List.of();
    }
}
