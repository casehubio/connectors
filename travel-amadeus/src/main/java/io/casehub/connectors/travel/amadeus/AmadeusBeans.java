package io.casehub.connectors.travel.amadeus;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.authn.StaticCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class AmadeusBeans {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Produces
    @ApplicationScoped
    AmadeusTravelPlatform amadeusTravelPlatform(StaticCredentialStore credentialStore) {
        var record = credentialStore.find("system", "amadeus", "default")
                .orElseThrow(() -> new IllegalStateException(
                        "No Amadeus credentials configured — expected provider 'amadeus'"));
        try {
            var cred = MAPPER.readTree(record.credential());
            var clientId = cred.path("clientId").asText();
            var clientSecret = cred.path("clientSecret").asText();
            var environment = cred.path("environment").asText("test");
            var client = new AmadeusClient(clientId, clientSecret, environment);
            return new AmadeusTravelPlatform(client);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Amadeus credentials", e);
        }
    }
}
