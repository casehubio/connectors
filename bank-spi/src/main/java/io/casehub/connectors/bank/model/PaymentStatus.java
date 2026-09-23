package io.casehub.connectors.bank.model;

public enum PaymentStatus {
    AUTHORIZATION_REQUIRED, AUTHORIZING, AUTHORIZED,
    EXECUTED, SETTLED, FAILED
}
