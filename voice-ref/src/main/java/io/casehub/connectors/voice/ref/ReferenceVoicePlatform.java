package io.casehub.connectors.voice.ref;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.model.TranscriptionResult;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.connectors.voice.spi.VoicePlatform;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReferenceVoicePlatform implements VoicePlatform {

    @Override
    public String id() {
        return "ref";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return capability == SpeechToText.class || capability == TextToSpeech.class;
    }

    @Override
    public SpeechToText speechToText() {
        return (audio, options) -> new TranscriptionResult(
                "Hello, this is a test transcription.", "en", 0.95);
    }

    @Override
    public TextToSpeech textToSpeech() {
        return (text, options) -> new SynthesisResult(silentWav(), "wav");
    }

    private static byte[] silentWav() {
        int sampleRate = 16000;
        int numSamples = sampleRate;
        int dataSize = numSamples * 2;
        ByteBuffer buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes()); buf.putInt(36 + dataSize);
        buf.put("WAVE".getBytes());
        buf.put("fmt ".getBytes()); buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(sampleRate); buf.putInt(sampleRate * 2);
        buf.putShort((short) 2); buf.putShort((short) 16);
        buf.put("data".getBytes()); buf.putInt(dataSize);
        return buf.array();
    }
}
