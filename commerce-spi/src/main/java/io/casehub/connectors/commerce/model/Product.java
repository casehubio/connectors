package io.casehub.connectors.commerce.model;

public record Product(
    String id,
    String name,
    String brand,
    String category,
    Money price,
    Double rating,
    Integer reviewCount,
    boolean inStock,
    String thumbnailUrl
) {}
