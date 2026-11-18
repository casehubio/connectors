package io.casehub.connectors.voice.model;

import java.util.List;

public record SynthesisOptions(
        String voice,
        String language,
        String audioFormat,
        boolean includePhonemes,
        List<TextMark> marks) {

    public SynthesisOptions {
        marks = marks != null ? List.copyOf(marks) : List.of();
    }

    public SynthesisOptions(String voice, String language, String audioFormat, boolean includePhonemes) {
        this(voice, language, audioFormat, includePhonemes, List.of());
    }

    public static SynthesisOptions defaults() {
        return new SynthesisOptions(null, null, "wav", false);
    }
}
