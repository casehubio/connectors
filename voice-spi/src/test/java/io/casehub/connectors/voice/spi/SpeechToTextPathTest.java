package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.model.TranscriptionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechToTextPathTest {

    @TempDir Path tempDir;

    @Test
    void defaultTranscribePathDelegatesToByteArray() throws IOException {
        Path audioFile = tempDir.resolve("test.wav");
        Files.write(audioFile, new byte[]{1, 2, 3, 4});

        SpeechToText stt = (audio, options) ->
                new TranscriptionResult("transcribed " + audio.length + " bytes", "en", 0.9);

        TranscriptionResult result = stt.transcribe(audioFile, TranscriptionOptions.defaults());
        assertThat(result.text()).isEqualTo("transcribed 4 bytes");
    }
}
