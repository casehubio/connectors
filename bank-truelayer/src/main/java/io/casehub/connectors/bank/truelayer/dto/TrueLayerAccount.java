package io.casehub.connectors.bank.truelayer.dto;

public record TrueLayerAccount(String accountId, String displayName,
                                String accountType, String currency) {}
