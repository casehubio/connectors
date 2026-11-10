package io.casehub.connectors.voice.sherpa;

record MelConfig(int sampleRate, int nFft, int hopLength, int nMels,
                 float fMin, float fMax) {}
