package io.casehub.connectors.mobile.model;

import java.time.Instant;

public record VoicemailMessage(
        String id,
        String from,
        String to,
        int durationSeconds,
        Instant timestamp,
        String audioFormat,
        String transcription) {
}
