package io.casehub.connectors.email.google.inbound;

import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;

import java.util.logging.Logger;

import static org.jose4j.jwa.AlgorithmConstraints.ConstraintType.PERMIT;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_USING_SHA256;

public class GoogleOidcJwtValidator implements JwtValidator {

    private static final Logger LOG = Logger.getLogger(GoogleOidcJwtValidator.class.getName());
    static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";

    private final JwtConsumer jwtConsumer;

    public GoogleOidcJwtValidator(String expectedAudience) {
        this(expectedAudience, new HttpsJwksVerificationKeyResolver(new HttpsJwks(GOOGLE_JWKS_URL)));
    }

    GoogleOidcJwtValidator(String expectedAudience, VerificationKeyResolver keyResolver) {
        this.jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setExpectedIssuers(true, "accounts.google.com", "https://accounts.google.com")
                .setExpectedAudience(expectedAudience)
                .setVerificationKeyResolver(keyResolver)
                .setJwsAlgorithmConstraints(PERMIT, RSA_USING_SHA256)
                .build();
    }

    @Override
    public boolean validate(String jwt) {
        try {
            jwtConsumer.processToClaims(jwt);
            return true;
        } catch (InvalidJwtException e) {
            LOG.warning("gmail-push: JWT validation failed: " + e.getMessage());
            return false;
        }
    }
}
