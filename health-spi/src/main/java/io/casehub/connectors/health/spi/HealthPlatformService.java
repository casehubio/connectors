package io.casehub.connectors.health.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HealthPlatformService {

    private final Map<String, HealthPlatform> platforms;

    public HealthPlatformService(List<HealthPlatform> platforms) {
        this.platforms = platforms.stream()
            .collect(Collectors.toMap(HealthPlatform::id, p -> p));
    }

    public HealthPlatform platform(String id) {
        var platform = platforms.get(id);
        if (platform == null) {
            throw new IllegalArgumentException("No health platform: " + id);
        }
        return platform;
    }

    public boolean supports(String id) {
        return platforms.containsKey(id);
    }

    public Set<String> ids() {
        return platforms.keySet();
    }
}
