package io.casehub.connectors.bank.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Transaction(String id, String accountId,
                          BigDecimal amount, TransactionDirection direction,
                          String currency,
                          String description, String merchantName,
                          String category,
                          LocalDate date, TransactionStatus status) {}
