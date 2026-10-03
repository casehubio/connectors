package io.casehub.connectors.location.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LocationPlatformService {

    private final Map<String, LocationPlatform> platforms;

    public LocationPlatformService(List<LocationPlatform> platforms) {
        this.platforms = platforms.stream()
            .collect(Collectors.toMap(LocationPlatform::id, p -> p));
    }

    public LocationPlatform platform(String id) {
        var platform = platforms.get(id);
        if (platform == null) {
            throw new IllegalArgumentException("No location platform: " + id);
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
