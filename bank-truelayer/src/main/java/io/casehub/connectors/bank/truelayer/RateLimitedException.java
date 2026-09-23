package io.casehub.connectors.bank.truelayer;

public class RateLimitedException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitedException(int retryAfterSeconds) {
        super("TrueLayer rate limit exceeded — retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
