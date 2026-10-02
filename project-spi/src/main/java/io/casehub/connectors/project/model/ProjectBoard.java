package io.casehub.connectors.project.model;

import java.util.Objects;

public record ProjectBoard(String id, String title) {
    public ProjectBoard { Objects.requireNonNull(id); }
}
