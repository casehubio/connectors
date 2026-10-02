package io.casehub.connectors.project.model;

import java.util.Objects;

public record OwnerRepo(String owner, String repo) {
    public OwnerRepo {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(repo);
    }
}
