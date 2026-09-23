package io.casehub.connectors.bank.model;

public record InitiatedPayment(String paymentId,
                                String hostedPaymentPageLink,
                                PaymentStatus status) {}
