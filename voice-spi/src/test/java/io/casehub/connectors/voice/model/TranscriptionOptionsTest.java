package io.casehub.connectors.voice.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptionOptionsTest {

    @Test
    void withVocabularyHintReturnsNewInstance() {
        var opts = TranscriptionOptions.defaults().withVocabularyHint("medical");
        assertThat(opts.vocabularyHint()).isEqualTo("medical");
        assertThat(opts.audioFormat()).isNull();
    }

    @Test
    void withLanguageHintPreservesOtherFields() {
        var opts = new TranscriptionOptions("wav", null, "base", "hint")
                .withLanguageHint("fr");
        assertThat(opts.languageHint()).isEqualTo("fr");
        assertThat(opts.audioFormat()).isEqualTo("wav");
        assertThat(opts.modelSize()).isEqualTo("base");
        assertThat(opts.vocabularyHint()).isEqualTo("hint");
    }

    @Test
    void threeArgConstructorSetsVocabularyHintToNull() {
        var opts = new TranscriptionOptions("wav", "en", "base");
        assertThat(opts.vocabularyHint()).isNull();
    }
}
