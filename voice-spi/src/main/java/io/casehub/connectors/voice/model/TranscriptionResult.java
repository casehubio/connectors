package io.casehub.connectors.voice.model;

import java.util.Objects;

public record TranscriptionResult(String text, String language, double confidence) {
    public TranscriptionResult {
        Objects.requireNonNull(text, "text");
    }
}
