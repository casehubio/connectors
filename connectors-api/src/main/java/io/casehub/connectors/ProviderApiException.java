package io.casehub.connectors;

public class ProviderApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;
    private final String provider;

    public ProviderApiException(int statusCode, String responseBody, String provider) {
        super(provider + " API error (HTTP " + statusCode + "): " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.provider = provider;
    }

    public int statusCode() { return statusCode; }
    public String responseBody() { return responseBody; }
    public String provider() { return provider; }
}
