package io.casehub.connectors.commerce.model;

import java.time.Instant;
import java.util.List;

public record Order(
    String id,
    OrderStatus status,
    List<OrderLineItem> lineItems,
    Money total,
    Instant createdAt,
    Instant updatedAt,
    String trackingNumber,
    String trackingUrl
) {}
