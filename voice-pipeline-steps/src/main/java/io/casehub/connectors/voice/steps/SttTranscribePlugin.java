package io.casehub.connectors.voice.steps;

import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.model.TranscriptionResult;
import io.casehub.connectors.voice.spi.RoutingVoicePlatformService;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;

@Plugin(value = "stt-transcribe", streaming = true,
        description = "Transcribe audio bytes to text via VoicePlatform STT")
public record SttTranscribePlugin(
        @Optional String language,
        @Optional String model) {

    @Execute
    public TranscriptionResult apply(byte[] audio, RoutingVoicePlatformService routing) {
        if (model != null) {
            var resolved = routing.resolve(model);
            var options = TranscriptionOptions.defaults()
                                              .withLanguageHint(language)
                                              .withModelSize(resolved.descriptor().voiceId());
            return resolved.platform().speechToText().transcribe(audio, options);
        }
        var options = TranscriptionOptions.defaults().withLanguageHint(language);
        return routing.defaultStt().speechToText().transcribe(audio, options);
    }
}
