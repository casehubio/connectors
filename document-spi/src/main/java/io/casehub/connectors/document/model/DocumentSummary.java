package io.casehub.connectors.document.model;

import java.time.Instant;

public record DocumentSummary(String id, String name, String folderId,
                              String contentType, long size,
                              Instant createdAt, Instant modifiedAt) {}
