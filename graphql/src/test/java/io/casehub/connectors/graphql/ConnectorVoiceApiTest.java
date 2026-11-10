package io.casehub.connectors.graphql;

import io.casehub.connectors.voice.model.PhonemeTiming;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.model.TranscriptionResult;
import io.casehub.connectors.voice.model.VoiceCapability;
import io.casehub.connectors.voice.model.VoiceDescriptor;
import io.casehub.connectors.voice.spi.InMemoryVoiceRegistry;
import io.casehub.connectors.voice.spi.ResolvedVoice;
import io.casehub.connectors.voice.spi.RoutingVoicePlatformService;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.connectors.voice.spi.VoicePlatform;
import io.casehub.connectors.voice.spi.VoicePlatformService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorVoiceApiTest {

    private static final byte[] SAMPLE_AUDIO = "fake-wav-data".getBytes(StandardCharsets.UTF_8);
    private static final String SAMPLE_BASE64 = Base64.getEncoder().encodeToString(SAMPLE_AUDIO);

    @Test
    void speak_withDefaultVoice_returnsSynthesisResult() {
        var api = apiWithDefaults();

        var result = api.speak("Hello world", null, null, "wav", false);

        assertThat(result.audioBase64()).isNotEmpty();
        assertThat(result.audioFormat()).isEqualTo("wav");
        assertThat(result.vendor()).isEqualTo("stub-voice");
        assertThat(result.phonemes()).isEmpty();
    }

    @Test
    void speak_withNamedVoice_resolvesAndSynthesises() {
        var api = apiWithDefaults();

        var result = api.speak("Hello", "narrator", null, "wav", false);

        assertThat(result.audioBase64()).isNotEmpty();
        assertThat(result.voiceId()).isEqualTo("narrator");
    }

    @Test
    void speak_withLanguageQuery_resolvesViaQuery() {
        var api = apiWithDefaults();

        var result = api.speak("Bonjour", null, "fr", "wav", false);

        assertThat(result.audioBase64()).isNotEmpty();
        assertThat(result.vendor()).isEqualTo("stub-voice");
    }

    @Test
    void speak_withPhonemes_includesPhonemeTiming() {
        var api = apiWithDefaults();

        var result = api.speak("Hello", null, null, "wav", true);

        assertThat(result.phonemes()).isNotEmpty();
        assertThat(result.phonemes().getFirst().phoneme()).isEqualTo("h");
    }

    @Test
    void transcribe_withDefaultPlatform_returnsTranscription() {
        var api = apiWithDefaults();

        var result = api.transcribe(SAMPLE_BASE64, "wav", null, null);

        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.confidence()).isGreaterThan(0.0);
    }

    @Test
    void transcribe_withNamedPlatform_delegatesToPlatform() {
        var api = apiWithDefaults();

        var result = api.transcribe(SAMPLE_BASE64, "wav", "stub-voice", null);

        assertThat(result.text()).isEqualTo("hello world");
    }

    @Test
    void listVoices_noFilters_returnsAll() {
        var api = apiWithDefaults();

        var result = api.listVoices(null, null, null, null, null, null);

        assertThat(result).hasSize(2);
    }

    @Test
    void listVoices_filteredByGender_returnsMatching() {
        var api = apiWithDefaults();

        var result = api.listVoices("female", null, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().gender()).isEqualTo("female");
    }

    @Test
    void speak_unsupportedCapability_throwsMcpCapabilityException() {
        var sttOnly = new StubVoicePlatform("stt-only", false, true);
        var api = apiWith(sttOnly);

        org.junit.jupiter.api.Assertions.assertThrows(
                io.casehub.platform.api.mcp.McpCapabilityException.class,
                () -> api.speak("Hello", null, null, "wav", false));
    }

    @Test
    void transcribe_unsupportedCapability_throwsMcpCapabilityException() {
        var ttsOnly = new StubVoicePlatform("tts-only", true, false);
        var api = apiWith(ttsOnly);

        org.junit.jupiter.api.Assertions.assertThrows(
                io.casehub.platform.api.mcp.McpCapabilityException.class,
                () -> api.transcribe(SAMPLE_BASE64, "wav", "tts-only", null));
    }

    // --- test infrastructure ---

    private ConnectorVoiceApi apiWithDefaults() {
        return apiWith(new StubVoicePlatform("stub-voice", true, true));
    }

    private ConnectorVoiceApi apiWith(VoicePlatform platform) {
        var voices = List.of(
                new VoiceDescriptor("narrator", platform.id(), "narrator",
                        "Narrator", "male", "british", 0.9,
                        List.of("en"), "narrative", 24000, 16, 1,
                        VoiceCapability.TTS, Map.of()),
                new VoiceDescriptor("alice", platform.id(), "alice",
                        "Alice", "female", "american", 0.85,
                        List.of("en", "fr"), "conversational", 24000, 16, 1,
                        VoiceCapability.TTS, Map.of()));

        var registry = new InMemoryVoiceRegistry();
        registry.replaceSource("test", 0, voices);
        var platformService = new VoicePlatformService(List.of(platform));
        var defaults = new io.casehub.connectors.voice.config.VoiceManifestDefaults(
                platform.id(), platform.id());
        var routingService = new RoutingVoicePlatformService(
                platformService, registry, Map.of(), defaults);

        var api = new ConnectorVoiceApi();
        api.routingService = routingService;
        api.platformService = platformService;
        api.voiceRegistry = registry;
        return api;
    }

    static class StubVoicePlatform implements VoicePlatform {
        private final String id;
        private final boolean supportsTts;
        private final boolean supportsStt;

        StubVoicePlatform(String id, boolean supportsTts, boolean supportsStt) {
            this.id = id;
            this.supportsTts = supportsTts;
            this.supportsStt = supportsStt;
        }

        @Override public String id() { return id; }

        @Override public boolean supports(Class<?> capability) {
            if (capability == TextToSpeech.class) return supportsTts;
            if (capability == SpeechToText.class) return supportsStt;
            return false;
        }

        @Override public SpeechToText speechToText() {
            return (audio, options) -> new TranscriptionResult("hello world", "en", 0.95);
        }

        @Override public TextToSpeech textToSpeech() {
            return (text, options) -> {
                var phonemes = options.includePhonemes()
                        ? List.of(new PhonemeTiming("h", 0, 50),
                                  new PhonemeTiming("E", 50, 100))
                        : List.<PhonemeTiming>of();
                return new SynthesisResult(SAMPLE_AUDIO, options.audioFormat() != null
                        ? options.audioFormat() : "wav", phonemes);
            };
        }
    }
}
