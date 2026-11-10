package io.casehub.connectors.voice.config;

import io.casehub.connectors.voice.spi.InMemoryVoiceRegistry;
import io.casehub.connectors.voice.spi.RoutingVoicePlatformService;
import io.casehub.connectors.voice.spi.VoicePlatform;
import io.casehub.connectors.voice.spi.VoicePlatformService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Optional;

@ApplicationScoped
public class VoiceConfigProducer {

    @Inject
    Instance<VoicePlatform> platforms;

    @ConfigProperty(name = "casehub.voice.profile")
    Optional<String> profile;

    @ConfigProperty(name = "casehub.voice.project-dir")
    Optional<String> projectDir;

    @Produces
    @ApplicationScoped
    public RoutingVoicePlatformService routingVoicePlatformService() {
        var platformService = new VoicePlatformService(platforms.stream().toList());
        var registry        = new InMemoryVoiceRegistry();
        var loader          = new VoiceManifestLoader();
        var dir             = projectDir.map(java.nio.file.Path::of).orElse(null);
        var manifest        = loader.load(dir, profile.orElse(null));
        var processor       = new VoiceManifestProcessor(registry);
        var result          = processor.process(manifest);
        return new RoutingVoicePlatformService(
                platformService, registry, result.aliases(), manifest.defaults());
    }
}
