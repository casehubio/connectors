package io.casehub.connectors.project.model;

import java.time.Instant;
import java.util.Objects;

public record Milestone(
    String id, int number, String title, String description,
    String state, Instant dueOn, int openIssues, int closedIssues
) {
    public Milestone { Objects.requireNonNull(title); }
}
