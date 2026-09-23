package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.bank.spi.AccountInformation;
import io.casehub.connectors.bank.spi.PaymentInitiation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrueLayerBankPlatformTest {

    TrueLayerBankPlatform platform = new TrueLayerBankPlatform(null, null);

    @Test
    void id_returnsTruelayer() {
        assertThat(platform.id()).isEqualTo("truelayer");
    }

    @Test
    void supports_accountInformation_returnsTrue() {
        assertThat(platform.supports(AccountInformation.class)).isTrue();
    }

    @Test
    void supports_paymentInitiation_returnsTrue() {
        assertThat(platform.supports(PaymentInitiation.class)).isTrue();
    }

    @Test
    void supports_unknownCapability_returnsFalse() {
        assertThat(platform.supports(String.class)).isFalse();
    }

    @Test
    void accountInformation_returnsNonNull() {
        var consentService = new TrueLayerConsentService("", "", "", null);
        var p = new TrueLayerBankPlatform(null, consentService);
        assertThat(p.accountInformation("user-1")).isNotNull();
    }

    @Test
    void paymentInitiation_returnsNonNull() {
        var consentService = new TrueLayerConsentService("", "", "", null);
        var p = new TrueLayerBankPlatform(null, consentService);
        assertThat(p.paymentInitiation("user-1")).isNotNull();
    }
}
