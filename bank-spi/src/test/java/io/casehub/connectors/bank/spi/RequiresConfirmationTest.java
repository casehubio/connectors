package io.casehub.connectors.bank.spi;

import io.casehub.connectors.bank.model.PaymentRequest;
import io.casehub.platform.api.confirmation.RequiresConfirmation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequiresConfirmationTest {

    @Test
    void initiatePaymentRequiresConfirmation() throws Exception {
        var method = PaymentInitiation.class.getMethod("initiatePayment", PaymentRequest.class);
        var annotation = method.getAnnotation(RequiresConfirmation.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.summary())
                .isEqualTo("Initiate payment of ${payment.amount} ${payment.currency} to ${payment.beneficiaryName}");
    }
}
