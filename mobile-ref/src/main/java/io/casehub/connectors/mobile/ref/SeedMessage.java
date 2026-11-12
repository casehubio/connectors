package io.casehub.connectors.mobile.ref;

import io.casehub.connectors.mobile.model.MessageDirection;
import io.casehub.connectors.mobile.model.MessageStatus;

import java.time.Instant;

record SeedMessage(String id, String from, String to, String body,
                   MessageStatus status, MessageDirection direction,
                   String channel, Instant timestamp) {
}
