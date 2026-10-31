package io.casehub.connectors.voice.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record SynthesisResult(byte[] audioData, String audioFormat, List<PhonemeTiming> phonemes) {

    public SynthesisResult {
        audioData = audioData == null ? new byte[0] : audioData.clone();
        Objects.requireNonNull(audioFormat, "audioFormat");
        phonemes = phonemes != null ? List.copyOf(phonemes) : List.of();
    }

    public SynthesisResult(byte[] audioData, String audioFormat) {
        this(audioData, audioFormat, List.of());
    }

    @Override
    public byte[] audioData() {
        return audioData.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {return true;}
        if (!(obj instanceof SynthesisResult other)) {return false;}
        return Objects.equals(audioFormat, other.audioFormat)
               && Arrays.equals(audioData, other.audioData)
               && Objects.equals(phonemes, other.phonemes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(audioData), audioFormat, phonemes);
    }
}
