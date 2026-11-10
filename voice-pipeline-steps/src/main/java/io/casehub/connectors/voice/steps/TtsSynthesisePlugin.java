package io.casehub.connectors.voice.steps;

import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.spi.RoutingVoicePlatformService;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;

@Plugin(value = "tts-synthesise", streaming = true,
        description = "Synthesise text to audio bytes via VoicePlatform TTS")
public record TtsSynthesisePlugin(
        @Optional String voice,
        @Optional String language) {

    @Execute
    public SynthesisResult apply(String text, RoutingVoicePlatformService routing) {
        if (voice != null) {
            var resolved = routing.resolve(voice);
            var options = new SynthesisOptions(
                    resolved.descriptor().voiceId(), language, "wav", false);
            return resolved.platform().textToSpeech().synthesise(text, options);
        }
        var options = new SynthesisOptions(null, language, "wav", false);
        return routing.defaultTts().textToSpeech().synthesise(text, options);
    }
}
