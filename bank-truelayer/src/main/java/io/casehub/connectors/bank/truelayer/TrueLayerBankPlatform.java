package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.bank.spi.AccountInformation;
import io.casehub.connectors.bank.spi.BankPlatform;
import io.casehub.connectors.bank.spi.PaymentInitiation;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TrueLayerBankPlatform implements BankPlatform {

    private final TrueLayerClient client;
    private final TrueLayerConsentService consentService;

    public TrueLayerBankPlatform(TrueLayerClient client,
                                  TrueLayerConsentService consentService) {
        this.client = client;
        this.consentService = consentService;
    }

    @Override
    public String id() {
        return "truelayer";
    }

    @Override
    public AccountInformation accountInformation(String userId) {
        return new TrueLayerAccountInformation(client, consentService, userId);
    }

    @Override
    public PaymentInitiation paymentInitiation(String userId) {
        return new TrueLayerPaymentInitiation(client, consentService, userId);
    }

    @Override
    public boolean supports(Class<?> capability) {
        return capability == AccountInformation.class
            || capability == PaymentInitiation.class;
    }
}
