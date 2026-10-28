package io.casehub.connectors;

public class SyncTokenExpiredException extends ProviderApiException {

    private final String expiredToken;

    public SyncTokenExpiredException(String expiredToken, String provider) {
        super(410, expiredToken, provider);
        this.expiredToken = expiredToken;
    }

    public String expiredToken() {
        return expiredToken;
    }
}
