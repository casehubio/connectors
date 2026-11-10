package io.casehub.connectors.voice.ref;

import io.casehub.connectors.voice.spi.SpeechDenoiser;

public class RefSpeechDenoiser implements SpeechDenoiser {

    @Override
    public float[] denoise(float[] samples, int sampleRate) {
        return samples;
    }
}
