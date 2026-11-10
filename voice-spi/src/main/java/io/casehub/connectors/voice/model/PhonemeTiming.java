package io.casehub.connectors.voice.model;

import java.util.Objects;

public record PhonemeTiming(String phoneme, long startMs, long endMs) {
    public PhonemeTiming {
        Objects.requireNonNull(phoneme, "phoneme");
        if (endMs < startMs) {
            throw new IllegalArgumentException("endMs (" + endMs + ") < startMs (" + startMs + ")");
        }
    }
}
