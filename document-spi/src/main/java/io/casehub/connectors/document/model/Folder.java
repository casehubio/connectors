package io.casehub.connectors.document.model;

import java.time.Instant;

public record Folder(String id, String name, String parentId, Instant createdAt) {}
