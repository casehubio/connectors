package io.casehub.connectors.web;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.web")
public interface WebPlatformConfig {

    InterventionConfig intervention();

    interface InterventionConfig {
        @WithDefault("10")
        int timeoutMinutes();
    }
}
