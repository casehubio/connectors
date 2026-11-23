package io.casehub.connectors.mobile.numbrrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.authn.StaticCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.Arrays;
import java.util.List;

public class NumbrrsMobileBeans {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Produces
    @ApplicationScoped
    NumbrrsMobilePlatform numbrrsMobilePlatform(StaticCredentialStore credentialStore) {
        var record = credentialStore.find("system", "numbrrs", "default")
                .orElseThrow(() -> new IllegalStateException(
                        "No Numbrrs credentials configured — expected provider 'numbrrs'"));
        try {
            var cred = MAPPER.readTree(record.credential());
            var apiKey = cred.path("api-key").asText("");
            var numbers = parseNumbers(cred.path("numbers").asText(""));
            var client = new NumbrrsClient(apiKey);
            return new NumbrrsMobilePlatform(client, numbers);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Numbrrs credentials", e);
        }
    }

    @Produces
    @ApplicationScoped
    NumbrrsInboundConnector numbrrsInboundConnector(StaticCredentialStore credentialStore) {
        var record = credentialStore.find("system", "numbrrs", "default");
        var signingSecret = record.map(r -> {
            try {
                return MAPPER.readTree(r.credential()).path("signing-secret").asText("");
            } catch (Exception e) {
                return "";
            }
        }).orElse("");
        return new NumbrrsInboundConnector(signingSecret);
    }

    private static List<String> parseNumbers(String numbers) {
        if (numbers.isBlank()) return List.of();
        return Arrays.stream(numbers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
