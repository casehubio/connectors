package io.casehub.connectors.bank.truelayer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TrueLayerBalance(String accountId,
                                BigDecimal available, BigDecimal current,
                                String currency, Instant updateTimestamp) {}
