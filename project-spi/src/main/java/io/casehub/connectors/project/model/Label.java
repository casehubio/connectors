package io.casehub.connectors.project.model;

import java.util.Objects;

public record Label(String id, String name, String color, String description) {
    public Label { Objects.requireNonNull(name); }
}
