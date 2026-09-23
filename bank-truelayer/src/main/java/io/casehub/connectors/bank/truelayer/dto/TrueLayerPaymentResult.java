package io.casehub.connectors.bank.truelayer.dto;

public record TrueLayerPaymentResult(String id, String resourceToken,
                                      String hostedPaymentPageLink,
                                      String status) {}
