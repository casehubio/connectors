package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.ProviderRateLimitedException;

public class RateLimitedException extends ProviderRateLimitedException {

    public RateLimitedException(int retryAfterSeconds) {
        super(retryAfterSeconds, "truelayer");
    }
}
