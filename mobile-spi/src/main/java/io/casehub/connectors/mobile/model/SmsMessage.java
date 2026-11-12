package io.casehub.connectors.mobile.model;

import java.time.Instant;

public record SmsMessage(
        String id,
        String from,
        String to,
        String body,
        MessageStatus status,
        MessageDirection direction,
        Instant timestamp) {
}
