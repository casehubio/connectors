package io.casehub.connectors.bank;

import io.casehub.connectors.bank.spi.BankPlatform;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@ApplicationScoped
public class BankBeans {

    @Produces
    @ApplicationScoped
    public BankPlatformService bankPlatformService(
            @All List<BankPlatform> platforms) {
        return new BankPlatformService(platforms);
    }
}
