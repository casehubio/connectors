package io.casehub.connectors.mobile.model;

public record TranscriptionResult(String text, String language, double confidence) {
}
