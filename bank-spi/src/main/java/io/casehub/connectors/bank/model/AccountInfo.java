package io.casehub.connectors.bank.model;

public record AccountInfo(String id, String name,
                          AccountType type, String currency) {}
