package io.casehub.connectors.bank.truelayer.dto;

import java.math.BigDecimal;

public record TrueLayerPaymentRequest(BigDecimal amountInMinor, String currency,
                                       String beneficiaryName,
                                       String sortCode, String accountNumber,
                                       String reference, String idempotencyKey) {}
