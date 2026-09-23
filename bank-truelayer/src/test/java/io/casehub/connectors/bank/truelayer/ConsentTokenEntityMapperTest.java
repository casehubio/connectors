package io.casehub.connectors.bank.truelayer;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentTokenEntityMapperTest {

    @Test
    void toEntity_and_toDomain_roundTrip() {
        Instant now = Instant.now();
        Instant accessExpiry = now.plusSeconds(3600);
        Instant consentExpiry = now.plusSeconds(86400L * 90);
        StoredConsent consent = new StoredConsent(
                "access-token", "refresh-token",
                accessExpiry, consentExpiry,
                List.of(ConsentScope.ACCOUNTS, ConsentScope.BALANCE), now);

        ConsentTokenEntity entity = ConsentTokenEntityMapper.toEntity(
                "user-1", consent);
        StoredConsent result = ConsentTokenEntityMapper.toDomain(entity);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.accessTokenExpiry()).isEqualTo(accessExpiry);
        assertThat(result.consentExpiry()).isEqualTo(consentExpiry);
        assertThat(result.scopes()).containsExactly(
                ConsentScope.ACCOUNTS, ConsentScope.BALANCE);
        assertThat(result.grantedAt()).isEqualTo(now);
    }

    @Test
    void toEntity_scopesSerialization() {
        StoredConsent consent = new StoredConsent(
                "t", "r", Instant.now(), Instant.now(),
                List.of(ConsentScope.ACCOUNTS, ConsentScope.PAYMENTS),
                Instant.now());
        ConsentTokenEntity entity = ConsentTokenEntityMapper.toEntity(
                "user-1", consent);
        assertThat(entity.getScopes()).isEqualTo("ACCOUNTS,PAYMENTS");
    }

    @Test
    void toDomain_nullRefreshToken() {
        StoredConsent consent = new StoredConsent(
                "t", null, Instant.now(), Instant.now(),
                List.of(ConsentScope.ACCOUNTS), Instant.now());
        ConsentTokenEntity entity = ConsentTokenEntityMapper.toEntity(
                "user-1", consent);
        StoredConsent result = ConsentTokenEntityMapper.toDomain(entity);
        assertThat(result.refreshToken()).isNull();
    }
}
