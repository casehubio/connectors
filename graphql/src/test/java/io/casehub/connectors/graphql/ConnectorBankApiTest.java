package io.casehub.connectors.graphql;

import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.bank.BankPlatformService;
import io.casehub.connectors.bank.spi.AccountInformation;
import io.casehub.connectors.bank.spi.BankPlatform;
import io.casehub.connectors.bank.spi.PaymentInitiation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorBankApiTest {

    @Test
    void listAccountsThrowsStructuredErrorWhenAispUnsupported() {
        var platform = new StubBankPlatform("limited-provider", false, true);
        var service = new BankPlatformService(List.of(platform));
        var api = new ConnectorBankApi();
        api.bankService = service;

        assertThatThrownBy(() -> api.listAccounts("limited-provider"))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("listAccounts");
                    assertThat(uce.capability()).isEqualTo("AccountInformation");
                    assertThat(uce.provider()).isEqualTo("limited-provider");
                    assertThat(uce.supportedCapabilities()).containsExactly("PaymentInitiation");
                });
    }

    private record StubBankPlatform(String id, boolean supportsAisp, boolean supportsPisp) implements BankPlatform {
        @Override
        public AccountInformation accountInformation(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentInitiation paymentInitiation(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean supports(Class<?> capability) {
            if (capability == AccountInformation.class) return supportsAisp;
            if (capability == PaymentInitiation.class) return supportsPisp;
            return false;
        }
    }
}
