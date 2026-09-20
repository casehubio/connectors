package io.casehub.connectors.bank;

import io.casehub.connectors.bank.spi.BankFeedPlatform;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

@ApplicationScoped
public class BankBeans {

    @Produces
    @ApplicationScoped
    public BankFeedPlatformService bankFeedPlatformService(
            @All List<BankFeedPlatform> platforms) {
        return new BankFeedPlatformService(platforms);
    }
}
