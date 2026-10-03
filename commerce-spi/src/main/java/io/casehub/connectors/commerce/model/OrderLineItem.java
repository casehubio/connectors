package io.casehub.connectors.commerce.model;

public record OrderLineItem(String productId, String productName, int quantity, Money unitPrice) {}
