package io.casehub.connectors.bank.truelayer;

import java.time.Instant;
import java.util.List;

public record ConsentInfo(String userId, ConsentStatus status,
                          Instant grantedAt, Instant expiresAt,
                          List<ConsentScope> scopes) {}
