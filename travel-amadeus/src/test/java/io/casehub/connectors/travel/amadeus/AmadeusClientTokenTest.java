package io.casehub.connectors.travel.amadeus;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AmadeusClientTokenTest {

    @Test
    void tokenHolder_isExpired_returnsTrueWhenPastExpiry() {
        var holder = new AmadeusClient.TokenHolder("token123",
                Instant.now().minusSeconds(10));
        assertThat(holder.isExpired()).isTrue();
    }

    @Test
    void tokenHolder_isExpired_returnsTrueWithin60SecondBuffer() {
        var holder = new AmadeusClient.TokenHolder("token123",
                Instant.now().plusSeconds(30));
        assertThat(holder.isExpired()).isTrue();
    }

    @Test
    void tokenHolder_isExpired_returnsFalseWhenFresh() {
        var holder = new AmadeusClient.TokenHolder("token123",
                Instant.now().plusSeconds(300));
        assertThat(holder.isExpired()).isFalse();
    }

    @Test
    void constructor_setsTestBaseUrl() {
        var client = new AmadeusClient("id", "secret", "test");
        assertThat(client.baseUrl()).isEqualTo("https://test.api.amadeus.com");
    }

    @Test
    void constructor_setsProductionBaseUrl() {
        var client = new AmadeusClient("id", "secret", "production");
        assertThat(client.baseUrl()).isEqualTo("https://api.amadeus.com");
    }

    @Test
    void clearToken_resetsTokenState() {
        var client = new AmadeusClient("id", "secret", "test");
        client.clearToken();
        // Should not throw — clearToken on null token is safe
    }
}
