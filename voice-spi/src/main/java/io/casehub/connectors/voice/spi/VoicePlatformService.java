package io.casehub.connectors.voice.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class VoicePlatformService {

    private final Map<String, VoicePlatform> platforms;

    public VoicePlatformService(List<VoicePlatform> platforms) {
        this.platforms = platforms.stream()
            .collect(Collectors.toMap(VoicePlatform::id, p -> p));
    }

    public VoicePlatform platform(String id) {
        var platform = platforms.get(id);
        if (platform == null) {
            throw new IllegalArgumentException("No voice platform: " + id);
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
