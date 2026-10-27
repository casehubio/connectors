package io.casehub.connectors.travel.google;

import io.casehub.platform.api.authn.StaticCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalTime;
import java.util.Arrays;

public class GoogleTransitBeans {

    @Produces
    @ApplicationScoped
    GoogleTransitTravelPlatform googleTransitTravelPlatform(
            StaticCredentialStore credentialStore,
            @ConfigProperty(name = "casehub.connectors.google-transit.departure-slots",
                    defaultValue = "06:00,09:00,12:00,15:00,18:00,21:00")
            String slotsConfig) {
        var record = credentialStore.find("system", "google-maps", "default")
                .orElseThrow(() -> new IllegalStateException(
                        "No Google Maps credentials configured — expected provider 'google-maps'"));
        var apiKey = record.credential();
        var client = new GoogleTransitClient(apiKey);
        var slots = Arrays.stream(slotsConfig.split(","))
                .map(String::trim)
                .map(LocalTime::parse)
                .toList();
        return new GoogleTransitTravelPlatform(client, slots);
    }
}
