package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.connectors.voice.spi.VoicePlatform;

import java.util.List;

public final class WhisperVoicePlatform implements VoicePlatform, AutoCloseable {

    private final SpeechToText stt;

    public WhisperVoicePlatform(SherpaConfig config) {
        this.stt = new SherpaOnnxSpeechToText(config);
    }

    WhisperVoicePlatform(SpeechToText stt) {
        this.stt = stt;
    }

    public static WhisperVoicePlatform withDefaults() {
        Provisioner.ensureNativeLibrary();
        if (Provisioner.isAutoDownloadEnabled()) {
            Provisioner.ensureModel("sherpa-onnx-whisper-tiny");
        }
        return new WhisperVoicePlatform(SherpaConfig.defaults());
    }

    @Override
    public String id() {
        return "whisper";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return capability == SpeechToText.class;
    }

    @Override
    public SpeechToText speechToText() {
        return stt;
    }

    @Override
    public TextToSpeech textToSpeech() {
        throw new UnsupportedCapabilityException(
            "textToSpeech", "TextToSpeech", "whisper", List.of("SpeechToText"));
    }

    @Override
    public void close() {
        if (stt instanceof AutoCloseable ac) {
            try { ac.close(); } catch (Exception ignored) {}
        }
    }
}
