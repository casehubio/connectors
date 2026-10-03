package io.casehub.connectors.commerce.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CommercePlatformService {

    private final Map<String, CommercePlatform> platforms;

    public CommercePlatformService(List<CommercePlatform> platforms) {
        this.platforms = platforms.stream()
            .collect(Collectors.toMap(CommercePlatform::id, p -> p));
    }

    public CommercePlatform platform(String id) {
        var platform = platforms.get(id);
        if (platform == null) {
            throw new IllegalArgumentException("No commerce platform: " + id);
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
