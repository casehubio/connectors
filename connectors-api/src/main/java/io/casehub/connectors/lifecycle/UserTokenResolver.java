package io.casehub.connectors.lifecycle;

import java.util.Optional;

public interface UserTokenResolver {
    String providerId();
    Optional<String> getAccessToken(String userId);
}
