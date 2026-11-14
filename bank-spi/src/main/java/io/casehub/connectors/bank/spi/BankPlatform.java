package io.casehub.connectors.bank.spi;

import io.casehub.connectors.PlatformExtensible;
import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "bank-platform",
    capabilities = {"accountInformation", "paymentInitiation"})
public interface BankPlatform extends PlatformExtensible {

    String id();

    AccountInformation accountInformation(String userId);

    PaymentInitiation paymentInitiation(String userId);

    boolean supports(Class<?> capability);
}
