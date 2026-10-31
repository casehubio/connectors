package io.casehub.connectors.voice.model;

public record TranscriptionOptions(
        String audioFormat,
        String languageHint,
        String modelSize,
        String vocabularyHint) {

    public TranscriptionOptions(String audioFormat, String languageHint, String modelSize) {
        this(audioFormat, languageHint, modelSize, null);
    }

    public static TranscriptionOptions defaults() {
        return new TranscriptionOptions(null, null, null, null);
    }

    public TranscriptionOptions withLanguageHint(String hint) {
        return new TranscriptionOptions(audioFormat, hint, modelSize, vocabularyHint);
    }

    public TranscriptionOptions withModelSize(String size) {
        return new TranscriptionOptions(audioFormat, languageHint, size, vocabularyHint);
    }

    public TranscriptionOptions withVocabularyHint(String hint) {
        return new TranscriptionOptions(audioFormat, languageHint, modelSize, hint);
    }
}
