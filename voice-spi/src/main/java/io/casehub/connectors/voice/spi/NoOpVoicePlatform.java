package io.casehub.connectors.voice.spi;

import java.util.List;

import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.model.TranscriptionResult;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpVoicePlatform implements VoicePlatform {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return false;
    }

    @Override
    public SpeechToText speechToText() {
        return NoOpSpeechToText.INSTANCE;
    }

    @Override
    public TextToSpeech textToSpeech() {
        return NoOpTextToSpeech.INSTANCE;
    }

    private enum NoOpSpeechToText implements SpeechToText {
        INSTANCE;

        @Override
        public TranscriptionResult transcribe(byte[] audio, TranscriptionOptions options) {
            throw new UnsupportedCapabilityException("transcribe", "SpeechToText", "none", List.of());
        }
    }

    private enum NoOpTextToSpeech implements TextToSpeech {
        INSTANCE;

        @Override
        public SynthesisResult synthesise(String text, SynthesisOptions options) {
            throw new UnsupportedCapabilityException("synthesise", "TextToSpeech", "none", List.of());
        }
    }
}
