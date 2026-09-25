package io.casehub.connectors.bank.ref;

import io.casehub.connectors.bank.spi.AccountInformation;
import io.casehub.connectors.bank.spi.BankPlatform;
import io.casehub.connectors.bank.spi.PaymentInitiation;

public class RefBankPlatform implements BankPlatform {

    private final BankBackend backend;

    public RefBankPlatform(BankBackend backend) {
        this.backend = backend;
    }

    @Override
    public String id() {
        return "ref";
    }

    @Override
    public AccountInformation accountInformation(String userId) {
        return new RefAccountInformation(backend);
    }

    @Override
    public PaymentInitiation paymentInitiation(String userId) {
        return new RefPaymentInitiation(backend);
    }

    @Override
    public boolean supports(Class<?> capability) {
        return capability == AccountInformation.class
            || capability == PaymentInitiation.class;
    }
}
