package io.casehub.connectors.contacts.google;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

@DefaultBean
@ApplicationScoped
public class ConfigGoogleCredentialResolver implements GoogleCredentialResolver {

    @Override
    public GoogleOAuthConfig resolve(String userId) {
        var config = ConfigProvider.getConfig();
        var prefix = "casehub.contacts.google.credentials." + userId;
        var refreshToken = config.getValue(prefix + ".refresh-token", String.class);
        var clientId = config.getValue(prefix + ".client-id", String.class);
        var clientSecret = config.getValue(prefix + ".client-secret", String.class);
        return new GoogleOAuthConfig(refreshToken, clientId, clientSecret);
    }
}
