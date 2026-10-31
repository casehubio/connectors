package io.casehub.connectors.voice.steps;

import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.spi.VoicePlatform;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;

@Plugin(value = "tts-synthesise", streaming = true,
        description = "Synthesise text to audio bytes via VoicePlatform TTS")
public record TtsSynthesisePlugin(
        @Optional String voice,
        @Optional String language) {

    @Execute
    public SynthesisResult apply(String text, VoicePlatform voicePlatform) {
        var options = new SynthesisOptions(voice, language, "wav", false);
        return voicePlatform.textToSpeech().synthesise(text, options);
    }
}
