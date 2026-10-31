package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.TranscriptionResult;

public interface RecognitionStream extends AutoCloseable {
    void acceptSamples(float[] samples, int sampleRate);
    boolean isEndpointDetected();
    String partialResult();
    TranscriptionResult finalResult();
    void close();
}
