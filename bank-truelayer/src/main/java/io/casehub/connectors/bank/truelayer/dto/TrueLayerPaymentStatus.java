package io.casehub.connectors.bank.truelayer.dto;

public record TrueLayerPaymentStatus(String id, String status,
                                      String failureReason) {}
