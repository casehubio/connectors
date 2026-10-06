package io.casehub.connectors.travel.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TravelPlatformService {

    private final Map<String, TravelPlatform> platforms;

    public TravelPlatformService(List<TravelPlatform> platforms) {
        this.platforms = platforms.stream()
            .collect(Collectors.toMap(TravelPlatform::id, p -> p));
    }

    public TravelPlatform platform(String id) {
        var platform = platforms.get(id);
        if (platform == null) {
            throw new IllegalArgumentException("No travel platform: " + id);
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
