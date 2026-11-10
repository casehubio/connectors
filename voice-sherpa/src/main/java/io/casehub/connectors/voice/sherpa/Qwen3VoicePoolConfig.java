package io.casehub.connectors.voice.sherpa;

import java.nio.file.Path;

public record Qwen3VoicePoolConfig(
    Path voiceDir,
    String modelVariant,
    int maxActive,
    int basePort
) {
    public Qwen3VoicePoolConfig {
        if (maxActive < 1) throw new IllegalArgumentException("maxActive must be >= 1");
        if (basePort < 1 || basePort > 65535) throw new IllegalArgumentException("invalid basePort");
    }

    public static Qwen3VoicePoolConfig defaults() {
        return new Qwen3VoicePoolConfig(
            Path.of(System.getProperty("user.home"), ".casehub", "voice-samples"),
            "0.6b-base", 3, 8800);
    }
}
