package io.casehub.connectors.mobile.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record Call(
        String id,
        String to,
        String from,
        CallStatus status,
        String transcript,
        List<TranscriptTurn> transcriptTurns,
        Duration duration,
        Instant startedAt,
        Instant endedAt,
        String failureReason) {
}
