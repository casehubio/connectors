package io.casehub.connectors.bank.model;

public sealed interface PaymentDestination {
    record UkAccount(String sortCode, String accountNumber)
            implements PaymentDestination {}
    record IbanAccount(String iban, String bic)
            implements PaymentDestination {}
}
