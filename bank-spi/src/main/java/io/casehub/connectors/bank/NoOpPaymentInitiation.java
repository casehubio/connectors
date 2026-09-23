package io.casehub.connectors.bank;

import io.casehub.connectors.bank.model.InitiatedPayment;
import io.casehub.connectors.bank.model.PaymentRequest;
import io.casehub.connectors.bank.model.PaymentStatus;
import io.casehub.connectors.bank.spi.PaymentInitiation;

public class NoOpPaymentInitiation implements PaymentInitiation {

    public static final NoOpPaymentInitiation INSTANCE = new NoOpPaymentInitiation();

    @Override
    public InitiatedPayment initiatePayment(PaymentRequest payment) {
        throw new UnsupportedOperationException("No bank platform configured");
    }

    @Override
    public PaymentStatus paymentStatus(String paymentId) {
        throw new UnsupportedOperationException("No bank platform configured");
    }
}
