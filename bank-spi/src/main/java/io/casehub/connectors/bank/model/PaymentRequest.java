package io.casehub.connectors.bank.model;

import java.math.BigDecimal;

public record PaymentRequest(String idempotencyKey,
                             BigDecimal amount, String currency,
                             String beneficiaryName,
                             PaymentDestination destination,
                             String reference) {}
