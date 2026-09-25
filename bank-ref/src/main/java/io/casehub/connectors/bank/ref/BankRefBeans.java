package io.casehub.connectors.bank.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class BankRefBeans {

    @Produces
    @ApplicationScoped
    public RefBankPlatform refBankPlatform(BankBackend backend) {
        return new RefBankPlatform(backend);
    }
}
