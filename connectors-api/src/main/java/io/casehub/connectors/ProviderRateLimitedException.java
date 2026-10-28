package io.casehub.connectors;

public class ProviderRateLimitedException extends ProviderApiException {

    private final int retryAfterSeconds;

    public ProviderRateLimitedException(int retryAfterSeconds, String provider) {
        super(429, "Rate limited — retry after " + retryAfterSeconds + "s", provider);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() { return retryAfterSeconds; }
}
