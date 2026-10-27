package io.casehub.connectors.commerce.amazon;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.authn.StaticCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class CommerceAmazonBeans {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Produces
    @ApplicationScoped
    AmazonCommercePlatform amazonCommercePlatform(StaticCredentialStore credentialStore) {
        var record = credentialStore.find("system", "amazon-paapi", "default")
            .orElseThrow(() -> new IllegalStateException(
                "No Amazon PA-API credentials configured — expected provider 'amazon-paapi'"));
        try {
            var cred = MAPPER.readTree(record.credential());
            return new AmazonCommercePlatform(
                cred.path("accessKey").asText(),
                cred.path("secretKey").asText(),
                cred.path("partnerTag").asText(),
                cred.path("host").asText("webservices.amazon.co.uk"),
                cred.path("region").asText("eu-west-1"),
                cred.path("marketplace").asText("www.amazon.co.uk"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Amazon PA-API credentials", e);
        }
    }
}
