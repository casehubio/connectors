package io.casehub.connectors.bank.model;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountBalance(String accountId,
                             BigDecimal available, BigDecimal current,
                             String currency, Instant asOf) {}
