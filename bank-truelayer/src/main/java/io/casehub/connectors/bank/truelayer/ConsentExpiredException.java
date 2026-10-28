package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.AuthFailureReason;
import io.casehub.connectors.ProviderAuthException;

public class ConsentExpiredException extends ProviderAuthException {

    private final String userId;

    public ConsentExpiredException(String userId) {
        super(AuthFailureReason.TOKEN_EXPIRED, "truelayer");
        this.userId = userId;
    }

    public String userId() { return userId; }
}
