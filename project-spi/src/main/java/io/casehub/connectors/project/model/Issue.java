package io.casehub.connectors.project.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Issue(
    String id, int number, String title, String body,
    String state, List<Label> labels, Milestone milestone,
    List<String> assignees, Instant createdAt, Instant updatedAt
) {
    public Issue {
        // title is required on create but null on partial updates
        labels = labels != null ? List.copyOf(labels) : List.of();
        assignees = assignees != null ? List.copyOf(assignees) : List.of();
    }
}
