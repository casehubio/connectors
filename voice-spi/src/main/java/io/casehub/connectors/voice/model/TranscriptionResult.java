package io.casehub.connectors.voice.model;

public record TranscriptionResult(String text, String language, double confidence) {}
