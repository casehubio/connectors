package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.model.TranscriptionResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface SpeechToText {

    TranscriptionResult transcribe(byte[] audio, TranscriptionOptions options);

    default TranscriptionResult transcribe(Path audioFile, TranscriptionOptions options) {
        try {
            byte[] audio = Files.readAllBytes(audioFile);
            return transcribe(audio, options);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
