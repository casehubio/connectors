package io.casehub.connectors.commerce.model;

public record CartItem(String productId, String productName, int quantity, Money unitPrice) {}
