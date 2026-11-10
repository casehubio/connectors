package io.casehub.connectors.voice.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Qwen3VoicePoolTest {

    @Test
    void configRejectsZeroMaxActive(@TempDir Path dir) {
        assertThatThrownBy(() -> new Qwen3VoicePoolConfig(dir, "0.6b-base", 0, 8800))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configRejectsInvalidPort(@TempDir Path dir) {
        assertThatThrownBy(() -> new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configDefaultsAreValid() {
        var config = Qwen3VoicePoolConfig.defaults();
        assertThat(config.maxActive()).isEqualTo(3);
        assertThat(config.basePort()).isEqualTo(8800);
        assertThat(config.modelVariant()).isEqualTo("0.6b-base");
    }

    @Test
    void discoversQvoiceFilesInDirectory(@TempDir Path dir) throws Exception {
        java.nio.file.Files.createFile(dir.resolve("alice.qvoice"));
        java.nio.file.Files.createFile(dir.resolve("bob.qvoice"));
        java.nio.file.Files.createFile(dir.resolve("readme.txt"));

        var config = new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 8800);
        var pool = Qwen3VoicePool.create(config);

        assertThat(pool.availableVoices()).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void emptyDirectoryReturnsNoVoices(@TempDir Path dir) {
        var config = new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 8800);
        var pool = Qwen3VoicePool.create(config);

        assertThat(pool.availableVoices()).isEmpty();
    }

    @Test
    void forVoiceRejectsUnknownName(@TempDir Path dir) throws Exception {
        java.nio.file.Files.createFile(dir.resolve("alice.qvoice"));

        var config = new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 8800);
        var pool = Qwen3VoicePool.create(config);

        assertThatThrownBy(() -> pool.forVoice("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown voice");
    }

    @Test
    void forVoiceReturnsStreamingTextToSpeech(@TempDir Path dir) throws Exception {
        java.nio.file.Files.createFile(dir.resolve("alice.qvoice"));

        var config = new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 8800);
        var pool = Qwen3VoicePool.create(config);

        var tts = pool.forVoice("alice");
        assertThat(tts).isInstanceOf(io.casehub.connectors.voice.spi.StreamingTextToSpeech.class);
    }

    @Test
    void portAllocationIsDeterministic(@TempDir Path dir) throws Exception {
        java.nio.file.Files.createFile(dir.resolve("alice.qvoice"));
        java.nio.file.Files.createFile(dir.resolve("bob.qvoice"));
        java.nio.file.Files.createFile(dir.resolve("charlie.qvoice"));

        var config = new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 8900);
        var pool = Qwen3VoicePool.create(config);

        assertThat(pool.allocatePort("alice")).isEqualTo(8900);
        assertThat(pool.allocatePort("bob")).isEqualTo(8901);
        assertThat(pool.allocatePort("charlie")).isEqualTo(8902);
    }
}
