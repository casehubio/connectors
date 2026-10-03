package io.casehub.connectors.commerce.model;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {}
