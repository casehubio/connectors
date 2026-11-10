package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.config.VoiceAliasDeclaration;
import io.casehub.connectors.voice.config.VoiceManifestDefaults;
import io.casehub.connectors.voice.model.VoiceCapability;
import io.casehub.connectors.voice.model.VoiceDescriptor;
import io.casehub.connectors.voice.model.VoiceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingVoicePlatformServiceTest {

    static final VoiceDescriptor HEART = new VoiceDescriptor(
        "af_heart", "ref", "af_heart", "Heart", "female", "american",
        4.5, List.of("en-US"), null, null, null, null, VoiceCapability.TTS, Map.of());

    static final VoiceDescriptor DANIEL = new VoiceDescriptor(
        "bm_daniel", "ref", "bm_daniel", "Daniel", "male", "british",
        4.3, List.of("en-GB"), null, null, null, null, VoiceCapability.TTS, Map.of());

    private RoutingVoicePlatformService routing;

    @BeforeEach
    void setUp() {
        var refPlatform = new StubVoicePlatform("ref");
        var platformService = new VoicePlatformService(List.of(refPlatform));
        var registry = new InMemoryVoiceRegistry();
        registry.replaceSource("test", 0, List.of(HEART, DANIEL));
        var aliases = Map.of(
            "alice", new VoiceAliasDeclaration("af_heart", null, null, null, null, null, null, null),
            "high-quality", new VoiceAliasDeclaration(null, 4.4, null, null, null, null, null, VoiceCapability.TTS)
        );
        var defaults = new VoiceManifestDefaults("ref", "ref");
        routing = new RoutingVoicePlatformService(platformService, registry, aliases, defaults);
    }

    @Test
    void resolveByVoiceName() {
        var resolved = routing.resolve("af_heart");
        assertThat(resolved.descriptor()).isEqualTo(HEART);
        assertThat(resolved.platform().id()).isEqualTo("ref");
    }

    @Test
    void resolveByDirectAlias() {
        var resolved = routing.resolve("alice");
        assertThat(resolved.descriptor()).isEqualTo(HEART);
    }

    @Test
    void resolveByCriteriaAlias() {
        var resolved = routing.resolve("high-quality");
        assertThat(resolved.descriptor()).isEqualTo(HEART);
        assertThat(resolved.descriptor().quality()).isGreaterThanOrEqualTo(4.4);
    }

    @Test
    void resolveByVendorKey() {
        var resolved = routing.resolve("ref");
        assertThat(resolved.platform().id()).isEqualTo("ref");
        assertThat(resolved.descriptor()).isNull();
    }

    @Test
    void resolveUnknownThrows() {
        assertThatThrownBy(() -> routing.resolve("nonexistent"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No voice or alias");
    }

    @Test
    void aliasCycleThrows() {
        var refPlatform     = new StubVoicePlatform("ref");
        var platformService = new VoicePlatformService(List.of(refPlatform));
        var registry        = new InMemoryVoiceRegistry();
        registry.replaceSource("test", 0, List.of(HEART));
        var cyclicAliases = Map.of(
                "a", new VoiceAliasDeclaration("b", null, null, null, null, null, null, null),
                "b", new VoiceAliasDeclaration("a", null, null, null, null, null, null, null)
                                  );
        var defaults      = new VoiceManifestDefaults("ref", "ref");
        var cyclicRouting = new RoutingVoicePlatformService(platformService, registry, cyclicAliases, defaults);
        assertThatThrownBy(() -> cyclicRouting.resolve("a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alias cycle detected");
    }


    @Test
    void resolveQueryByGender() {
        var query = VoiceQuery.builder().gender("male").build();
        var resolved = routing.resolveQuery(query);
        assertThat(resolved.descriptor()).isEqualTo(DANIEL);
    }

    @Test
    void resolveQueryNoMatchThrows() {
        var query = VoiceQuery.builder().vendor("openai").build();
        assertThatThrownBy(() -> routing.resolveQuery(query))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultTts() {
        assertThat(routing.defaultTts().id()).isEqualTo("ref");
    }

    @Test
    void defaultStt() {
        assertThat(routing.defaultStt().id()).isEqualTo("ref");
    }

    @Test
    void selectBestPrefersHighestQuality() {
        var query = VoiceQuery.builder().capability(VoiceCapability.TTS).build();
        var resolved = routing.resolveQuery(query);
        assertThat(resolved.descriptor().quality()).isEqualTo(4.5);
    }

    static class StubVoicePlatform implements VoicePlatform {
        private final String platformId;
        StubVoicePlatform(String id) { this.platformId = id; }
        @Override public String id() { return platformId; }
        @Override public boolean supports(Class<?> capability) { return true; }
        @Override public SpeechToText speechToText() { return (a, o) -> null; }
        @Override public TextToSpeech textToSpeech() { return (t, o) -> null; }
    }
}
