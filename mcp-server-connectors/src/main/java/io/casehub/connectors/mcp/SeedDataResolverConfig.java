package io.casehub.connectors.mcp;

import io.casehub.connectors.seed.SeedDataResolver;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;
import java.util.Optional;

@ApplicationScoped
public class SeedDataResolverConfig {

    @ConfigProperty(name = "casehub.ref.seed-dir")
    Optional<String> seedDir;

    void onStart(@Observes StartupEvent ev) {
        seedDir.map(Path::of).ifPresent(SeedDataResolver::setSeedDir);
    }
}
