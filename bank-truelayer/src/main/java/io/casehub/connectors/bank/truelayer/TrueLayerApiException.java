package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.ProviderApiException;

public class TrueLayerApiException extends ProviderApiException {

    public TrueLayerApiException(int statusCode, String message) {
        super(statusCode, message, "truelayer");
    }
}
