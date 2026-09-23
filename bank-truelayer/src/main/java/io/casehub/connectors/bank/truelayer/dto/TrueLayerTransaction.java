package io.casehub.connectors.bank.truelayer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TrueLayerTransaction(String transactionId, String accountId,
                                    BigDecimal amount, String currency,
                                    String transactionType,
                                    String description, String merchantName,
                                    String transactionCategory,
                                    LocalDate timestamp, String status) {}
