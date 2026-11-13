package io.casehub.connectors.mobile.dial;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.authn.StaticCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.logging.Logger;

public class DialBeans {

    private static final Logger LOG = Logger.getLogger(DialBeans.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Produces
    @ApplicationScoped
    DialMobilePlatform dialMobilePlatform(StaticCredentialStore credentialStore) {
        var record = credentialStore.find("system", "dial", "default");
        if (record.isEmpty()) {
            LOG.warning("No Dial credentials configured — Dial MobilePlatform will return errors at call time");
            var client = new DialClient("");
            var webSocket = new DialWebSocket("");
            return new DialMobilePlatform(client, webSocket);
        }
        try {
            var cred = MAPPER.readTree(record.get().credential());
            var apiKey = cred.path("api-key").asText();
            var client = new DialClient(apiKey);
            var webSocket = new DialWebSocket(apiKey);
            return new DialMobilePlatform(client, webSocket);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Dial credentials", e);
        }
    }
}
