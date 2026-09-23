package io.casehub.connectors.bank;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.bank.spi.AccountInformation;
import io.casehub.connectors.bank.spi.BankPlatform;
import io.casehub.connectors.bank.spi.PaymentInitiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankPlatformServiceTest {

    static class StubPlatform implements BankPlatform {
        private final String platformId;
        StubPlatform(String id) { this.platformId = id; }
        @Override public String id() { return platformId; }
        @Override public AccountInformation accountInformation(String userId) {
            return NoOpAccountInformation.INSTANCE;
        }
        @Override public PaymentInitiation paymentInitiation(String userId) {
            return NoOpPaymentInitiation.INSTANCE;
        }
        @Override public boolean supports(Class<?> capability) {
            return capability == AccountInformation.class;
        }
    }

    @Test
    void platform_knownId_returnsPlatform() {
        var service = new BankPlatformService(List.of(new StubPlatform("truelayer")));
        assertThat(service.platform("truelayer").id()).isEqualTo("truelayer");
    }

    @Test
    void platform_unknownId_throws() {
        var service = new BankPlatformService(List.of(new StubPlatform("truelayer")));
        assertThatThrownBy(() -> service.platform("plaid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plaid")
                .hasMessageContaining("truelayer");
    }

    @Test
    void supports_knownId_returnsTrue() {
        var service = new BankPlatformService(List.of(new StubPlatform("truelayer")));
        assertThat(service.supports("truelayer")).isTrue();
    }

    @Test
    void supports_unknownId_returnsFalse() {
        var service = new BankPlatformService(List.of(new StubPlatform("truelayer")));
        assertThat(service.supports("plaid")).isFalse();
    }

    @Test
    void ids_returnsAllRegistered() {
        var service = new BankPlatformService(
                List.of(new StubPlatform("truelayer"), new StubPlatform("plaid")));
        assertThat(service.ids()).containsExactlyInAnyOrder("truelayer", "plaid");
    }

    @Test
    void duplicateId_throwsAtConstruction() {
        assertThatThrownBy(() -> new BankPlatformService(
                List.of(new StubPlatform("truelayer"), new StubPlatform("truelayer"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("truelayer");
    }
}
