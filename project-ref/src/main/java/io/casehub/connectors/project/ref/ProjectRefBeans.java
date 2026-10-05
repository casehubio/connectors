package io.casehub.connectors.project.ref;

import io.casehub.connectors.project.spi.ProjectPlatform;
import io.casehub.connectors.project.spi.ProjectPlatformService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ProjectRefBeans {

    @Produces
    @ApplicationScoped
    RefProjectPlatform refProjectPlatform(ProjectBackend backend) {
        return new RefProjectPlatform(backend);
    }

    @Produces
    @ApplicationScoped
    ProjectPlatformService projectPlatformService(Instance<ProjectPlatform> platforms) {
        return new ProjectPlatformService(platforms.stream().toList());
    }
}
