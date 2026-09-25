package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.bank.spi.AccountInformation;
import io.casehub.connectors.bank.spi.BankPlatform;
import io.casehub.connectors.bank.spi.PaymentInitiation;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(TrueLayerTestProfile.class)
class TrueLayerTestProfileTest {

    @Inject
    BankPlatform bankPlatform;

    @Test
    void bankPlatform_injected() {
        assertThat(bankPlatform).isNotNull();
        assertThat(bankPlatform.id()).isEqualTo("truelayer");
    }

    @Test
    void supports_bothCapabilities() {
        assertThat(bankPlatform.supports(AccountInformation.class)).isTrue();
        assertThat(bankPlatform.supports(PaymentInitiation.class)).isTrue();
    }

    @Test
    void accountInformation_returnsNonNull() {
        assertThat(bankPlatform.accountInformation("test-user")).isNotNull();
    }

    @Test
    void paymentInitiation_returnsNonNull() {
        assertThat(bankPlatform.paymentInitiation("test-user")).isNotNull();
    }
}
