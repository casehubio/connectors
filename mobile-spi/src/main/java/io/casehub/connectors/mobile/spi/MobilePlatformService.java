package io.casehub.connectors.mobile.spi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class MobilePlatformService {

    private final Map<String, MobilePlatform> platforms;

    @Inject
    public MobilePlatformService(List<MobilePlatform> all) {
        var map = new LinkedHashMap<String, MobilePlatform>();
        for (var p : all) {
            if (!"none".equals(p.id())) {
                map.put(p.id(), p);
            }
        }
        this.platforms = Map.copyOf(map);
    }

    public MobilePlatform platform(String id) {
        var p = platforms.get(id);
        if (p == null) {
            throw new IllegalArgumentException("Unknown mobile platform: " + id + ". Available: " + platforms.keySet());
        }
        return p;
    }

    public boolean supports(String id) {
        return platforms.containsKey(id);
    }

    public Set<String> ids() {
        return platforms.keySet();
    }
}
