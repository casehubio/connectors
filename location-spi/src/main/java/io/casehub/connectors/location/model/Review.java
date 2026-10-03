package io.casehub.connectors.location.model;

public record Review(String author, Double rating, String text, long timeMillis) {}
