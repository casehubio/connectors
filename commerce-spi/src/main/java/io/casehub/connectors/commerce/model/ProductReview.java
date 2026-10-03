package io.casehub.connectors.commerce.model;

public record ProductReview(String author, double rating, String text, long timestampMs) {}
