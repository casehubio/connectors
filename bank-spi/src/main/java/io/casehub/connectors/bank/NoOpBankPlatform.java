package io.casehub.connectors.bank;

import io.casehub.connectors.bank.spi.AccountInformation;
import io.casehub.connectors.bank.spi.BankPlatform;
import io.casehub.connectors.bank.spi.PaymentInitiation;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpBankPlatform implements BankPlatform {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public AccountInformation accountInformation(String userId) {
        return NoOpAccountInformation.INSTANCE;
    }

    @Override
    public PaymentInitiation paymentInitiation(String userId) {
        return NoOpPaymentInitiation.INSTANCE;
    }

    @Override
    public boolean supports(Class<?> capability) {
        return false;
    }
}
