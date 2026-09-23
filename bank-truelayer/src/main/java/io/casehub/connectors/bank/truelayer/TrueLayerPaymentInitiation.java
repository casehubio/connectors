package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.bank.model.InitiatedPayment;
import io.casehub.connectors.bank.model.PaymentDestination;
import io.casehub.connectors.bank.model.PaymentRequest;
import io.casehub.connectors.bank.model.PaymentStatus;
import io.casehub.connectors.bank.spi.PaymentInitiation;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerPaymentRequest;

import java.math.BigDecimal;

public class TrueLayerPaymentInitiation implements PaymentInitiation {

    private final TrueLayerClient client;
    private final TrueLayerConsentService consentService;
    private final String userId;

    TrueLayerPaymentInitiation(TrueLayerClient client,
                                TrueLayerConsentService consentService,
                                String userId) {
        this.client = client;
        this.consentService = consentService;
        this.userId = userId;
    }

    @Override
    public InitiatedPayment initiatePayment(PaymentRequest payment) {
        verifyPispConsent();
        PaymentDestination.UkAccount uk = switch (payment.destination()) {
            case PaymentDestination.UkAccount a -> a;
            case PaymentDestination.IbanAccount ignored ->
                    throw new UnsupportedOperationException(
                            "TrueLayer does not yet support IBAN payments");
        };

        BigDecimal amountInMinor = payment.amount()
                .multiply(BigDecimal.valueOf(100));
        if (amountInMinor.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "Payment amount " + payment.amount()
                    + " has sub-penny precision — cannot convert to minor units");
        }

        var tlRequest = new TrueLayerPaymentRequest(
                amountInMinor, payment.currency(),
                payment.beneficiaryName(),
                uk.sortCode(), uk.accountNumber(),
                payment.reference(), payment.idempotencyKey());

        var result = client.createPayment(tlRequest);

        return new InitiatedPayment(
                result.id(),
                result.hostedPaymentPageLink(),
                mapPaymentStatus(result.status()));
    }

    @Override
    public PaymentStatus paymentStatus(String paymentId) {
        var status = client.paymentStatus(paymentId);
        return mapPaymentStatus(status.status());
    }

    private void verifyPispConsent() {
        ConsentInfo consent = consentService.consentStatus(userId);
        if (consent == null || consent.status() != ConsentStatus.ACTIVE) {
            throw new ConsentExpiredException(userId);
        }
        if (!consent.scopes().contains(ConsentScope.PAYMENTS)) {
            throw new ConsentExpiredException(userId);
        }
    }

    private PaymentStatus mapPaymentStatus(String tlStatus) {
        return switch (tlStatus.toLowerCase()) {
            case "authorization_required" -> PaymentStatus.AUTHORIZATION_REQUIRED;
            case "authorizing" -> PaymentStatus.AUTHORIZING;
            case "authorized" -> PaymentStatus.AUTHORIZED;
            case "executed" -> PaymentStatus.EXECUTED;
            case "settled" -> PaymentStatus.SETTLED;
            case "failed" -> PaymentStatus.FAILED;
            default -> PaymentStatus.AUTHORIZATION_REQUIRED;
        };
    }
}
