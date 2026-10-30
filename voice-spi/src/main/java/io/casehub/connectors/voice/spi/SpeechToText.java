package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.model.TranscriptionResult;

public interface SpeechToText {

    TranscriptionResult transcribe(byte[] audio, TranscriptionOptions options);
}
