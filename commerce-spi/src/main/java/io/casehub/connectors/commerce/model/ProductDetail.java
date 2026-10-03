package io.casehub.connectors.commerce.model;

import java.util.List;
import java.util.Map;

public record ProductDetail(
    String id,
    String name,
    String brand,
    String sku,
    String category,
    String description,
    Money price,
    Double rating,
    Integer reviewCount,
    boolean inStock,
    int stockQuantity,
    List<ProductImage> images,
    List<ProductReview> reviews,
    Map<String, String> specifications,
    String url
) {}
