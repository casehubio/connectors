package io.casehub.connectors.voice.model;

import java.util.Objects;

public record MarkTiming(String id, long offsetMs) {
    public MarkTiming {
        Objects.requireNonNull(id, "id");
        if (offsetMs < 0) {
            throw new IllegalArgumentException("offsetMs (" + offsetMs + ") < 0");
        }
    }
}
