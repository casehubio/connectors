package io.casehub.connectors.voice.model;

public record SynthesisOptions(
        String voice,
        String language,
        String audioFormat,
        boolean includePhonemes) {}
