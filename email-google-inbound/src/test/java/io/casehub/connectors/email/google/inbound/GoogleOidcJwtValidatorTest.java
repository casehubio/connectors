package io.casehub.connectors.email.google.inbound;

import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOidcJwtValidatorTest {

    private static RsaJsonWebKey testKey;
    private static RsaJsonWebKey wrongKey;

    @BeforeAll
    static void generateKeys() throws Exception {
        testKey = RsaJwkGenerator.generateJwk(2048);
        testKey.setKeyId("test-key");
        wrongKey = RsaJwkGenerator.generateJwk(2048);
        wrongKey.setKeyId("wrong-key");
    }

    @Test
    void acceptsValidSignedJwt() throws Exception {
        var validator = validatorWithTestKey("test-audience");
        String jwt = signedJwt("accounts.google.com", "test-audience", 10);
        assertThat(validator.validate(jwt)).isTrue();
    }

    @Test
    void acceptsHttpsIssuer() throws Exception {
        var validator = validatorWithTestKey("test-audience");
        String jwt = signedJwt("https://accounts.google.com", "test-audience", 10);
        assertThat(validator.validate(jwt)).isTrue();
    }

    @Test
    void rejectsWrongSignature() throws Exception {
        var validator = validatorWithTestKey("test-audience");
        String jwt = signedJwtWithKey(wrongKey, "accounts.google.com", "test-audience", 10);
        assertThat(validator.validate(jwt)).isFalse();
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        var validator = validatorWithTestKey("test-audience");
        String jwt = signedJwt("accounts.google.com", "test-audience", 10);
        String[] parts = jwt.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "A" + "." + parts[2];
        assertThat(validator.validate(tampered)).isFalse();
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        var validator = validatorWithTestKey("test-audience");
        String jwt = signedJwt("accounts.google.com", "test-audience", -5);
        assertThat(validator.validate(jwt)).isFalse();
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        var validator = validatorWithTestKey("expected-audience");
        String jwt = signedJwt("accounts.google.com", "wrong-audience", 10);
        assertThat(validator.validate(jwt)).isFalse();
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        var validator = validatorWithTestKey("test-audience");
        String jwt = signedJwt("evil.example.com", "test-audience", 10);
        assertThat(validator.validate(jwt)).isFalse();
    }

    @Test
    void rejectsMalformedJwt() {
        var validator = validatorWithTestKey("test-audience");
        assertThat(validator.validate("not.a.jwt")).isFalse();
    }

    @Test
    void rejectsUnsignedJwt() {
        var validator = validatorWithTestKey("test-audience");
        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"accounts.google.com\",\"aud\":\"test-audience\",\"exp\":9999999999}".getBytes());
        assertThat(validator.validate(header + "." + payload + ".")).isFalse();
    }

    private GoogleOidcJwtValidator validatorWithTestKey(String audience) {
        var keyResolver = new JwksVerificationKeyResolver(List.of(testKey));
        return new GoogleOidcJwtValidator(audience, keyResolver);
    }

    private String signedJwt(String issuer, String audience, float expirationMinutes) throws Exception {
        return signedJwtWithKey(testKey, issuer, audience, expirationMinutes);
    }

    private String signedJwtWithKey(RsaJsonWebKey key, String issuer, String audience,
                                     float expirationMinutes) throws Exception {
        JwtClaims claims = new JwtClaims();
        claims.setIssuer(issuer);
        claims.setAudience(audience);
        claims.setExpirationTimeMinutesInTheFuture(expirationMinutes);
        claims.setGeneratedJwtId();

        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(key.getPrivateKey());
        jws.setKeyIdHeaderValue(key.getKeyId());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        return jws.getCompactSerialization();
    }
}
