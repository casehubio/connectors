package io.casehub.connectors.project.model;

import java.util.Objects;

public record ProjectColumn(String id, String name, int position) {
    public ProjectColumn { Objects.requireNonNull(id); }
}
