package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.VoiceDescriptor;

public record ResolvedVoice(VoicePlatform platform, VoiceDescriptor descriptor) {}
