package io.casehub.connectors.voice.spi;

public interface SpeechDenoiser {
    float[] denoise(float[] samples, int sampleRate);
}
