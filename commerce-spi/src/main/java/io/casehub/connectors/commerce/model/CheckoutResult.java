package io.casehub.connectors.commerce.model;

public record CheckoutResult(String orderId, OrderStatus status, Money total) {}
