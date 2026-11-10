package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.TranscriptionOptions;

public interface StreamingSpeechToText {
    RecognitionStream startStream(TranscriptionOptions options);
}
