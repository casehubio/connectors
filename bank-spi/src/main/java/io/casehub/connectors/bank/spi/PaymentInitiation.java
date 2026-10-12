package io.casehub.connectors.bank.spi;

import io.casehub.connectors.bank.model.InitiatedPayment;
import io.casehub.connectors.bank.model.PaymentRequest;
import io.casehub.connectors.bank.model.PaymentStatus;
import io.casehub.platform.api.confirmation.RequiresConfirmation;

public interface PaymentInitiation {

    @RequiresConfirmation(summary = "Initiate payment of ${payment.amount} ${payment.currency} to ${payment.beneficiaryName}")
    InitiatedPayment initiatePayment(PaymentRequest payment);

    PaymentStatus paymentStatus(String paymentId);
}
