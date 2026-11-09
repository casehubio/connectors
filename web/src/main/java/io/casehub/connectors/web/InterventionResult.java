package io.casehub.connectors.web;

public record InterventionResult(
    String id,
    String url,
    String reason,
    String screenshotRef,
    String submittedAt
) {}
