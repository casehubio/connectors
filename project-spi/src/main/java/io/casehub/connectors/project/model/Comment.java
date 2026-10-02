package io.casehub.connectors.project.model;

import java.time.Instant;
import java.util.Objects;

public record Comment(String id, String body, String author, Instant createdAt, Instant updatedAt) {
    public Comment { Objects.requireNonNull(body); }
}
