package io.casehub.connectors.commerce.model;

import java.util.List;

public record ShoppingCart(String id, List<CartItem> items, Money total) {}
