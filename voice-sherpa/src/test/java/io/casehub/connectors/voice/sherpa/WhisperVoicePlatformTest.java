package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.connectors.voice.spi.VoicePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhisperVoicePlatformTest {

    private static final SpeechToText STUB_STT = (audio, options) -> null;

    @Test
    void id_returnsWhisper() {
        var platform = new WhisperVoicePlatform(STUB_STT);
        assertThat(platform.id()).isEqualTo("whisper");
    }

    @Test
    void supports_sttTrue() {
        var platform = new WhisperVoicePlatform(STUB_STT);
        assertThat(platform.supports(SpeechToText.class)).isTrue();
    }

    @Test
    void supports_ttsFalse() {
        var platform = new WhisperVoicePlatform(STUB_STT);
        assertThat(platform.supports(TextToSpeech.class)).isFalse();
    }

    @Test
    void textToSpeech_throwsUnsupported() {
        var platform = new WhisperVoicePlatform(STUB_STT);
        assertThatThrownBy(platform::textToSpeech)
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void speechToText_returnsStub() {
        var platform = new WhisperVoicePlatform(STUB_STT);
        assertThat(platform.speechToText()).isSameAs(STUB_STT);
    }

    @Test
    @EnabledIf("isSherpaAvailable")
    void withDefaults_createsWorkingPlatform() {
        var platform = WhisperVoicePlatform.withDefaults();
        try {
            assertThat(platform.speechToText()).isNotNull();
            assertThat(platform.speechToText()).isInstanceOf(SpeechToText.class);
        } finally {
            platform.close();
        }
    }

    @Test
    void implementsVoicePlatform() {
        var platform = new WhisperVoicePlatform(STUB_STT);
        assertThat(platform).isInstanceOf(VoicePlatform.class);
        assertThat(platform).isInstanceOf(AutoCloseable.class);
    }

    static boolean isSherpaAvailable() {
        return SherpaLibrary.isAvailable();
    }
}
