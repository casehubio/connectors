package io.casehub.connectors.travel.model;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {}
