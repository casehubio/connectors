package io.casehub.connectors.bank.truelayer;

public class ConsentExpiredException extends RuntimeException {

    public ConsentExpiredException(String userId) {
        super("Consent expired or not found for user '" + userId
              + "' — re-consent required via browser redirect");
    }
}
