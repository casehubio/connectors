package io.casehub.connectors.bank.spi;

import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "bank-platform",
    capabilities = {"accountInformation", "paymentInitiation"})
public interface BankPlatform {

    String id();

    AccountInformation accountInformation(String userId);

    PaymentInitiation paymentInitiation(String userId);

    boolean supports(Class<?> capability);
}
