package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.PhonemeTiming;

import java.util.List;

@FunctionalInterface
public interface PhonemeAligner {
    List<PhonemeTiming> align(String text, byte[] audioData, int sampleRate);
}
