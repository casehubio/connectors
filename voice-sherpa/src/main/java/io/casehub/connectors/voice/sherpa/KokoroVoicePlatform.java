package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.connectors.voice.spi.VoicePlatform;

import java.util.List;

public final class KokoroVoicePlatform implements VoicePlatform, AutoCloseable {

    private final TextToSpeech tts;

    public KokoroVoicePlatform(KokoroConfig config) {
        this.tts = new KokoroTextToSpeech(config);
    }

    KokoroVoicePlatform(TextToSpeech tts) {
        this.tts = tts;
    }

    public static KokoroVoicePlatform withDefaults() {
        Provisioner.ensureNativeLibrary();
        var modelDir = Provisioner.ensureKokoroModel("kokoro-multi-lang-v1_0");
        return new KokoroVoicePlatform(KokoroConfig.defaults(modelDir));
    }

    @Override
    public String id() {
        return "kokoro";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return capability == TextToSpeech.class;
    }

    @Override
    public SpeechToText speechToText() {
        throw new UnsupportedCapabilityException(
                "speechToText", "SpeechToText", "kokoro", List.of("TextToSpeech"));
    }

    @Override
    public TextToSpeech textToSpeech() {
        return tts;
    }

    @Override
    public void close() {
        if (tts instanceof AutoCloseable ac) {
            try {ac.close();} catch (Exception ignored) {}
        }
    }
}
