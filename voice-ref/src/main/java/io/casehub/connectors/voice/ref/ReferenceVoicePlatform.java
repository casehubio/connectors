package io.casehub.connectors.voice.ref;

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
        return (audio, options) -> {
            String raw;
            if (audio.length > 44 && audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F') {
                raw = new String(audio, 44, audio.length - 44, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                raw = new String(audio, java.nio.charset.StandardCharsets.UTF_8);
            }
            String text = raw.startsWith("[voice:") ? raw.substring(raw.indexOf(']') + 1) : raw;
            return new TranscriptionResult(text, "en", 0.95);
        };
    }

    @Override
    public TextToSpeech textToSpeech() {
        return (text, options) -> {
            String voiceTag = options != null && options.voice() != null ? "[voice:" + options.voice() + "]" : "";
            byte[] payload  = (voiceTag + text).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] wav      = new byte[44 + payload.length];
            int    dataSize = payload.length;
            int    fileSize = 36 + dataSize;
            wav[0]  = 'R';
            wav[1]  = 'I';
            wav[2]  = 'F';
            wav[3]  = 'F';
            wav[4]  = (byte) fileSize;
            wav[5]  = (byte) (fileSize >> 8);
            wav[6]  = (byte) (fileSize >> 16);
            wav[7]  = (byte) (fileSize >> 24);
            wav[8]  = 'W';
            wav[9]  = 'A';
            wav[10] = 'V';
            wav[11] = 'E';
            wav[12] = 'f';
            wav[13] = 'm';
            wav[14] = 't';
            wav[15] = ' ';
            wav[16] = 16;
            wav[20] = 1;
            wav[22] = 1;
            wav[24] = (byte) 0x80;
            wav[25] = 0x3E;
            wav[28] = (byte) 0x00;
            wav[29] = 0x7D;
            wav[32] = 2;
            wav[34] = 16;
            wav[36] = 'd';
            wav[37] = 'a';
            wav[38] = 't';
            wav[39] = 'a';
            wav[40] = (byte) dataSize;
            wav[41] = (byte) (dataSize >> 8);
            wav[42] = (byte) (dataSize >> 16);
            wav[43] = (byte) (dataSize >> 24);
            System.arraycopy(payload, 0, wav, 44, payload.length);
            return new SynthesisResult(wav, "wav");
        };
    }

}
