package io.casehub.connectors.document.model;

import java.time.Instant;

public record DocumentMetadata(String id, String name, String folderId,
                               String contentType, long size,
                               String owner, String webViewLink,
                               Instant createdAt, Instant modifiedAt) {}
