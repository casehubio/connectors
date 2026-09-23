package io.casehub.connectors.bank.truelayer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ConsentTokenEntityMapper {

    public static StoredConsent toDomain(ConsentTokenEntity entity) {
        List<ConsentScope> scopes = Arrays.stream(entity.getScopes().split(","))
                .map(String::trim)
                .map(ConsentScope::valueOf)
                .toList();
        return new StoredConsent(
                entity.getAccessToken(),
                entity.getRefreshToken(),
                entity.getAccessTokenExpiry(),
                entity.getConsentExpiry(),
                scopes,
                entity.getGrantedAt());
    }

    public static ConsentTokenEntity toEntity(String userId,
                                               StoredConsent consent) {
        String scopes = consent.scopes().stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
        return new ConsentTokenEntity(
                userId,
                consent.accessToken(),
                consent.refreshToken(),
                consent.accessTokenExpiry(),
                consent.consentExpiry(),
                scopes,
                consent.grantedAt());
    }
}
