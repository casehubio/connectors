package io.casehub.connectors.mobile.model;

import java.time.Instant;
import java.util.List;

public record RichMessage(
        String id,
        String from,
        String to,
        String body,
        MessagingChannel channel,
        List<MediaAttachment> media,
        MessageStatus status,
        MessageDirection direction,
        Instant timestamp) {
}
