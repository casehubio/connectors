package io.casehub.connectors.bank.truelayer;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Reusable test profile for apps that include bank-truelayer.
 * Provides all OIDC client, consent, and devservice config needed
 * to boot Quarkus in tests without real TrueLayer credentials.
 *
 * Usage: {@code @TestProfile(TrueLayerTestProfile.class)}
 *
 * Consuming apps add the test-jar dependency:
 * <pre>{@code
 * <dependency>
 *     <groupId>io.casehub</groupId>
 *     <artifactId>casehub-connectors-bank-truelayer</artifactId>
 *     <version>${casehub.version}</version>
 *     <type>test-jar</type>
 *     <scope>test</scope>
 * </dependency>
 * }</pre>
 */
public class TrueLayerTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                Map.entry("quarkus.oidc-client.truelayer.auth-server-url",
                        "https://auth.truelayer-sandbox.com"),
                Map.entry("quarkus.oidc-client.truelayer.client-id", "test"),
                Map.entry("quarkus.oidc-client.truelayer.credentials.secret", "test"),
                Map.entry("quarkus.oidc-client.truelayer.grant.type", "client"),
                Map.entry("quarkus.oidc-client.truelayer.discovery-enabled", "false"),
                Map.entry("quarkus.oidc-client.truelayer.token-path", "/connect/token"),
                Map.entry("quarkus.keycloak.devservices.enabled", "false"),
                Map.entry("casehub.connectors.bank.truelayer.client-id", "test-client-id"),
                Map.entry("casehub.connectors.bank.truelayer.client-secret", "test-client-secret"),
                Map.entry("casehub.connectors.bank.truelayer.auth-url",
                        "https://auth.truelayer-sandbox.com"),
                Map.entry("casehub.connectors.bank.truelayer.base-url",
                        "https://api.truelayer-sandbox.com"));
    }
}
