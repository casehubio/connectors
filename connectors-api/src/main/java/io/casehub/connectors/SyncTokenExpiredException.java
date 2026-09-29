package io.casehub.connectors;

public class SyncTokenExpiredException extends RuntimeException {

    private final String expiredToken;

    public SyncTokenExpiredException(String expiredToken) {
        super("Sync token expired — full resync required");
        this.expiredToken = expiredToken;
    }

    public String expiredToken() {
        return expiredToken;
    }
}
