package io.casehub.connectors.voice.spi;

public interface VoiceActivityFilter extends AutoCloseable {
    float[] filterChunk(float[] samples, int sampleRate);
    void reset();
    void close();
}
