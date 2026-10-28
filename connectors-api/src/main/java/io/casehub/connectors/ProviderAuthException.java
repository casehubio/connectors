package io.casehub.connectors;

public class ProviderAuthException extends ProviderApiException {

    private final AuthFailureReason reason;

    public ProviderAuthException(AuthFailureReason reason, String provider) {
        super(reason == AuthFailureReason.TOKEN_EXPIRED ? 401 : 403,
              reason.name(), provider);
        this.reason = reason;
    }

    public AuthFailureReason reason() { return reason; }
}
