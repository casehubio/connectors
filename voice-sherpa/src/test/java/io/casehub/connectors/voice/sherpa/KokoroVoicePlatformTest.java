package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.connectors.voice.spi.VoicePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KokoroVoicePlatformTest {

    private static final TextToSpeech STUB_TTS = (text, options) -> null;

    @Test
    void id_returnsKokoro() {
        var platform = new KokoroVoicePlatform(STUB_TTS);
        assertThat(platform.id()).isEqualTo("kokoro");
    }

    @Test
    void supports_ttsTrue() {
        var platform = new KokoroVoicePlatform(STUB_TTS);
        assertThat(platform.supports(TextToSpeech.class)).isTrue();
    }

    @Test
    void supports_sttFalse() {
        var platform = new KokoroVoicePlatform(STUB_TTS);
        assertThat(platform.supports(SpeechToText.class)).isFalse();
    }

    @Test
    void speechToText_throwsUnsupported() {
        var platform = new KokoroVoicePlatform(STUB_TTS);
        assertThatThrownBy(platform::speechToText)
                .isInstanceOf(UnsupportedCapabilityException.class);
    }

    @Test
    void textToSpeech_returnsStub() {
        var platform = new KokoroVoicePlatform(STUB_TTS);
        assertThat(platform.textToSpeech()).isSameAs(STUB_TTS);
    }

    @Test
    @EnabledIf("isSherpaAvailable")
    void withDefaults_createsWorkingPlatform() {
        var platform = KokoroVoicePlatform.withDefaults();
        try {
            assertThat(platform.textToSpeech()).isNotNull();
            assertThat(platform.textToSpeech()).isInstanceOf(TextToSpeech.class);
        } finally {
            platform.close();
        }
    }

    @Test
    void implementsVoicePlatform() {
        var platform = new KokoroVoicePlatform(STUB_TTS);
        assertThat(platform).isInstanceOf(VoicePlatform.class);
        assertThat(platform).isInstanceOf(AutoCloseable.class);
    }

    static boolean isSherpaAvailable() {
        return SherpaLibrary.isAvailable();
    }
}
