package io.casehub.connectors.bank.truelayer;

public class TrueLayerApiException extends RuntimeException {

    private final int statusCode;

    public TrueLayerApiException(int statusCode, String message) {
        super("TrueLayer API error (HTTP " + statusCode + "): " + message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
