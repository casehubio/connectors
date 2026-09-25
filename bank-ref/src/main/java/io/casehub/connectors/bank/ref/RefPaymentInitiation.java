package io.casehub.connectors.bank.ref;

import io.casehub.connectors.bank.model.InitiatedPayment;
import io.casehub.connectors.bank.model.PaymentRequest;
import io.casehub.connectors.bank.model.PaymentStatus;
import io.casehub.connectors.bank.spi.PaymentInitiation;

class RefPaymentInitiation implements PaymentInitiation {

    private final BankBackend backend;

    RefPaymentInitiation(BankBackend backend) {
        this.backend = backend;
    }

    @Override
    public InitiatedPayment initiatePayment(PaymentRequest payment) {
        return backend.initiatePayment(payment);
    }

    @Override
    public PaymentStatus paymentStatus(String paymentId) {
        return backend.paymentStatus(paymentId);
    }
}
