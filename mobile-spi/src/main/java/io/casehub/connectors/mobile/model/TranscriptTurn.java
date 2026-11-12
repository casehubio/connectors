package io.casehub.connectors.mobile.model;

public record TranscriptTurn(Speaker speaker, String text, long startMs, long endMs) {
}
