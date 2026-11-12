package io.casehub.connectors.mobile.ref;

import java.time.Instant;

record SeedVoicemail(String id, String from, String to, int durationSeconds,
                     Instant timestamp, String audioFormat, String transcription) {
}
