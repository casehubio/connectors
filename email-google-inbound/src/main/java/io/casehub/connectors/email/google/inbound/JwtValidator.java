package io.casehub.connectors.email.google.inbound;

public interface JwtValidator {

    boolean validate(String jwt);
}
