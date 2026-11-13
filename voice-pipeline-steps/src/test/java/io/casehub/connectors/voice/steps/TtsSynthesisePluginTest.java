package io.casehub.connectors.voice.steps;

import io.casehub.connectors.voice.config.VoiceManifestDefaults;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.model.VoiceCapability;
import io.casehub.connectors.voice.model.VoiceDescriptor;
import io.casehub.connectors.voice.ref.ReferenceVoicePlatform;
import io.casehub.connectors.voice.spi.InMemoryVoiceRegistry;
import io.casehub.connectors.voice.spi.RoutingVoicePlatformService;
import io.casehub.connectors.voice.spi.VoicePlatformService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TtsSynthesisePluginTest {

    private final RoutingVoicePlatformService routing = buildRouting();

    @Test
    void synthesisesTextViaVoicePlatform() {
        var             plugin = new TtsSynthesisePlugin(null, "en");
        SynthesisResult result = plugin.apply("Hello world", routing);
        assertThat(result.audioData()).isNotEmpty();
        assertThat(result.audioFormat()).isEqualTo("wav");
        assertThat(result.audioData().length).isGreaterThan(44);
        assertThat(new String(result.audioData(), 0, 4, java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("RIFF");
        assertThat(new String(result.audioData(), 44, result.audioData().length - 44,
                              java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("Hello world");
    }

    @Test
    void passesVoiceAndLanguage() {
        var             plugin = new TtsSynthesisePlugin("af_heart", "fr");
        SynthesisResult result = plugin.apply("Bonjour", routing);
        assertThat(result).isNotNull();
        assertThat(result.audioData()).isNotEmpty();
        String payload = new String(result.audioData(), 44, result.audioData().length - 44, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(payload).startsWith("[voice:af_heart]");
        assertThat(payload).endsWith("Bonjour");
    }

    static RoutingVoicePlatformService buildRouting() {
        var platform        = new ReferenceVoicePlatform();
        var platformService = new VoicePlatformService(List.of(platform));
        var registry        = new InMemoryVoiceRegistry();
        registry.replaceSource("test", 0, List.of(new VoiceDescriptor(
                "af_heart", platform.id(), "af_heart", "Heart", "female", "american",
                4.5, List.of("en-US"), null, null, null, null, VoiceCapability.TTS, Map.of())));
        return new RoutingVoicePlatformService(
                platformService, registry, Map.of(), new VoiceManifestDefaults(platform.id(), platform.id()));
    }
}
