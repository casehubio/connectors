package io.casehub.connectors.voice.config;

import io.casehub.connectors.voice.model.VoiceCapability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class VoiceManifestLoaderTest {

    @Test
    void mergeHigherPriorityOverrides(@TempDir Path tempDir) throws IOException {
        var loader = new VoiceManifestLoader();

        var low = """
            voices:
              af_heart:
                vendor: kokoro
                voice-id: "0"
                name: Heart
                quality: 4.0
                capability: tts
            defaults:
              tts-vendor: kokoro
              stt-vendor: whisper
            """;
        var high = """
            voices:
              af_heart:
                vendor: openai
                voice-id: nova
                name: Nova
                quality: 4.8
                capability: tts
            """;

        Files.writeString(tempDir.resolve("low.yaml"), low);
        Files.writeString(tempDir.resolve("high.yaml"), high);

        var lowManifest = loader.loadFile(tempDir.resolve("low.yaml"));
        var highManifest = loader.loadFile(tempDir.resolve("high.yaml"));

        var merged = loader.merge(List.of(
            new VoiceManifestLoader.PrioritizedManifest(lowManifest, 10),
            new VoiceManifestLoader.PrioritizedManifest(highManifest, 30)));

        assertThat(merged.voices().get("af_heart").vendor()).isEqualTo("openai");
        assertThat(merged.voices().get("af_heart").voiceId()).isEqualTo("nova");
        assertThat(merged.defaults().ttsVendor()).isEqualTo("kokoro");
    }

    @Test
    void injectsIdFromMapKey(@TempDir Path tempDir) throws IOException {
        var yaml = """
            voices:
              my_voice:
                vendor: kokoro
                voice-id: "7"
                name: Custom
                quality: 3.5
                capability: tts
            """;
        Files.writeString(tempDir.resolve("voice-config.yaml"), yaml);
        var loader = new VoiceManifestLoader();
        var manifest = loader.loadFile(tempDir.resolve("voice-config.yaml"));
        assertThat(manifest.voices().get("my_voice").id()).isEqualTo("my_voice");
    }

    @Test
    void parsesAliases(@TempDir Path tempDir) throws IOException {
        var yaml = """
            aliases:
              alice:
                voice: af_heart
              high-quality:
                min-quality: 4.0
                prefer-vendor: kokoro
                capability: tts
            """;
        Files.writeString(tempDir.resolve("voice-config.yaml"), yaml);
        var loader = new VoiceManifestLoader();
        var manifest = loader.loadFile(tempDir.resolve("voice-config.yaml"));
        assertThat(manifest.aliases().get("alice").voice()).isEqualTo("af_heart");
        assertThat(manifest.aliases().get("high-quality").minQuality()).isEqualTo(4.0);
        assertThat(manifest.aliases().get("high-quality").capability()).isEqualTo(VoiceCapability.TTS);
    }

    @Test
    void parsesProviders(@TempDir Path tempDir) throws IOException {
        var yaml = """
            providers:
              - vendor: kokoro
                model-dir: /models/kokoro
              - vendor: whisper
                model-dir: /models/whisper
            """;
        Files.writeString(tempDir.resolve("voice-config.yaml"), yaml);
        var loader = new VoiceManifestLoader();
        var manifest = loader.loadFile(tempDir.resolve("voice-config.yaml"));
        assertThat(manifest.providers()).hasSize(2);
        assertThat(manifest.providers().get(0).vendor()).isEqualTo("kokoro");
        assertThat(manifest.providers().get(0).modelDir()).isEqualTo("/models/kokoro");
    }

    @Test
    void loadFromProjectDirOverridesDefaults(@TempDir Path tempDir) throws IOException {
        var yaml = """
            voices:
              af_heart:
                vendor: openai
                voice-id: nova
                name: Nova
                quality: 5.0
                capability: tts
            """;
        Files.writeString(tempDir.resolve("voice-config.yaml"), yaml);
        var loader = new VoiceManifestLoader();
        var manifest = loader.load(tempDir, null);
        var heart = manifest.voices().get("af_heart");
        assertThat(heart.vendor()).isEqualTo("openai");
    }

    @Test
    void profileFileIsLoaded(@TempDir Path tempDir) throws IOException {
        var base = """
            voices:
              af_heart:
                vendor: kokoro
                voice-id: "0"
                name: Heart
                quality: 4.0
                capability: tts
            """;
        var profile = """
            voices:
              af_heart:
                vendor: openai
                voice-id: nova
                name: Nova
                quality: 5.0
                capability: tts
            """;
        Files.writeString(tempDir.resolve("voice-config.yaml"), base);
        Files.writeString(tempDir.resolve("voice-config-prod.yaml"), profile);
        var loader = new VoiceManifestLoader();
        var manifest = loader.load(tempDir, "prod");
        assertThat(manifest.voices().get("af_heart").vendor()).isEqualTo("openai");
    }

    @Test
    void emptyFileReturnsEmptyManifest(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("empty.yaml"), "");
        var loader = new VoiceManifestLoader();
        var manifest = loader.loadFile(tempDir.resolve("empty.yaml"));
        assertThat(manifest.voices()).isEmpty();
        assertThat(manifest.providers()).isEmpty();
    }
}
