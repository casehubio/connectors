package io.casehub.connectors.voice.config;

import io.casehub.connectors.voice.model.VoiceCapability;
import io.casehub.connectors.voice.spi.InMemoryVoiceRegistry;
import io.casehub.connectors.voice.spi.RoutingVoicePlatformService;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.connectors.voice.spi.VoicePlatform;
import io.casehub.connectors.voice.spi.VoicePlatformService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceConfigProducerTest {

    @Test
    void seedCatalogLoadsVoices() {
        var loader   = new VoiceManifestLoader();
        var manifest = loader.load(null, null);
        assertThat(manifest.voices()).containsKey("af_heart");
        assertThat(manifest.voices()).containsKey("bm_daniel");
        assertThat(manifest.voices().get("af_heart").vendor()).isEqualTo("kokoro");
        assertThat(manifest.voices().get("af_heart").capability()).isEqualTo(VoiceCapability.TTS);
    }

    @Test
    void endToEndRoutingWithSeedCatalog() {
        var refPlatform     = new StubVoicePlatform("ref");
        var kokoroPlatform  = new StubVoicePlatform("kokoro");
        var whisperPlatform = new StubVoicePlatform("whisper");
        var platformService = new VoicePlatformService(List.of(refPlatform, kokoroPlatform, whisperPlatform));
        var registry        = new InMemoryVoiceRegistry();
        var loader          = new VoiceManifestLoader();
        var manifest        = loader.load(null, null);
        var processor       = new VoiceManifestProcessor(registry);
        var result          = processor.process(manifest);
        var routing = new RoutingVoicePlatformService(
                platformService, registry, result.aliases(), manifest.defaults());

        var resolved = routing.resolve("af_heart");
        assertThat(resolved.platform().id()).isEqualTo("kokoro");
        assertThat(resolved.descriptor().voiceId()).isEqualTo("af_heart");
        assertThat(resolved.descriptor().name()).isEqualTo("Heart");
    }

    static class StubVoicePlatform implements VoicePlatform {
        private final String platformId;

        StubVoicePlatform(String id)                 {this.platformId = id;}

        @Override
        public String id()                           {return platformId;}

        @Override
        public boolean supports(Class<?> capability) {return true;}

        @Override
        public SpeechToText speechToText()           {return (a, o) -> null;}

        @Override
        public TextToSpeech textToSpeech()           {return (t, o) -> null;}
    }
}
