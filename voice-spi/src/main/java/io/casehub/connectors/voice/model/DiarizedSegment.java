package io.casehub.connectors.voice.model;

public record DiarizedSegment(long startMs, long endMs, String speakerLabel,
                               float[] samples, int sampleRate) {}
