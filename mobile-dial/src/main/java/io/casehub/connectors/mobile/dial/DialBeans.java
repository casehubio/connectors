package io.casehub.connectors.mobile.dial;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.authn.StaticCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class DialBeans {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Produces
    @ApplicationScoped
    DialMobilePlatform dialMobilePlatform(StaticCredentialStore credentialStore) {
        var record = credentialStore.find("system", "dial", "default")
                .orElseThrow(() -> new IllegalStateException(
                        "No Dial credentials configured — expected provider 'dial'"));
        try {
            var cred = MAPPER.readTree(record.credential());
            var apiKey = cred.path("api-key").asText();
            var client = new DialClient(apiKey);
            var webSocket = new DialWebSocket(apiKey);
            return new DialMobilePlatform(client, webSocket);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Dial credentials", e);
        }
    }
}
