package io.casehub.connectors.spring;

import io.casehub.connectors.project.spi.ProjectPlatform;
import io.casehub.connectors.project.spi.ProjectPlatformService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ProjectPlatformService.class)
public class ProjectRefManualConfig {

    @Bean
    @ConditionalOnMissingBean
    ProjectPlatformService projectPlatformService(ObjectProvider<ProjectPlatform> platforms) {
        return new ProjectPlatformService(platforms.orderedStream().toList());
    }
}
