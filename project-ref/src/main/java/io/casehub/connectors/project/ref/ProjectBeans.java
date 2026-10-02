package io.casehub.connectors.project.ref;

import io.casehub.connectors.project.spi.ProjectPlatform;
import io.casehub.connectors.project.spi.ProjectPlatformService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

public class ProjectBeans {

    @Produces
    @ApplicationScoped
    ProjectBackend projectBackend() {
        return ProjectBackend.withTestData();
    }

    @Produces
    @ApplicationScoped
    ProjectPlatformService projectPlatformService(Instance<ProjectPlatform> platforms) {
        return new ProjectPlatformService(platforms.stream().toList());
    }
}
