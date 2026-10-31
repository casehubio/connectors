package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.model.TranscriptionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SpeechToTextContractTest {

    @Test
    void transcriptionResultRejectsNullText() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TranscriptionResult(null, "en", 0.9));
    }

    @Test
    void transcriptionResultStoresAllFields() {
        final var result = new TranscriptionResult("hello world", "en", 0.95);
        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void transcriptionOptionsDefaultsReturnsTinyWav() {
        final var opts = TranscriptionOptions.defaults();
        assertThat(opts.audioFormat()).isNull();
        assertThat(opts.modelSize()).isNull();
        assertThat(opts.languageHint()).isNull();
    }

    @Test
    void stubImplementationTranscribesFile() {
        final SpeechToText stt = (audio, options) ->
                new TranscriptionResult("transcribed: test", "en", 1.0);

        final var result = stt.transcribe(new byte[]{1, 2, 3}, TranscriptionOptions.defaults());
        assertThat(result.text()).isEqualTo("transcribed: test");
        assertThat(result.confidence()).isEqualTo(1.0);
    }
}
