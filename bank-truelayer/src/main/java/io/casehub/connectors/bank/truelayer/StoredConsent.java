package io.casehub.connectors.bank.truelayer;

import java.time.Instant;
import java.util.List;

public record StoredConsent(String accessToken, String refreshToken,
                            Instant accessTokenExpiry, Instant consentExpiry,
                            List<ConsentScope> scopes, Instant grantedAt) {}
