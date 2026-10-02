package io.casehub.connectors.project.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProjectPlatformService {

    private final Map<String, ProjectPlatform> platforms;

    public ProjectPlatformService(List<ProjectPlatform> platforms) {
        this.platforms = platforms.stream()
            .collect(Collectors.toMap(ProjectPlatform::id, p -> p));
    }

    public ProjectPlatform platform(String id) {
        var platform = platforms.get(id);
        if (platform == null) {
            throw new IllegalArgumentException("No project platform: " + id);
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
