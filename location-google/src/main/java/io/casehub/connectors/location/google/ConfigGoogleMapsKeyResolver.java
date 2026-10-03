package io.casehub.connectors.location.google;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

@DefaultBean
@ApplicationScoped
public class ConfigGoogleMapsKeyResolver implements GoogleMapsKeyResolver {

    @Override
    public GoogleMapsConfig resolve(String userId) {
        var config = ConfigProvider.getConfig();
        var prefix = "casehub.location.google.credentials." + userId;
        var apiKey = config.getValue(prefix + ".api-key", String.class);
        return new GoogleMapsConfig(apiKey);
    }
}
