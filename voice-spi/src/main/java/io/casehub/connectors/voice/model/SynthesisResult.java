package io.casehub.connectors.voice.model;

import java.util.Arrays;
import java.util.Objects;

public record SynthesisResult(byte[] audioData, String audioFormat) {

    public SynthesisResult {
        audioData = audioData == null ? new byte[0] : audioData.clone();
    }

    @Override
    public byte[] audioData() {
        return audioData.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SynthesisResult other)) return false;
        return Objects.equals(audioFormat, other.audioFormat)
                && Arrays.equals(audioData, other.audioData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(audioData), audioFormat);
    }
}
