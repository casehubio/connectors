package io.casehub.connectors.it;

import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.model.TranscriptionResult;
import io.casehub.connectors.voice.ref.ReferenceVoicePlatform;
import io.casehub.connectors.voice.spi.NoOpVoicePlatform;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.connectors.voice.spi.VoicePlatform;
import io.casehub.connectors.voice.spi.VoicePlatformService;
import io.casehub.connectors.voice.steps.SttTranscribePlugin;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.StreamingStep;
import io.casehub.yaml.plugin.api.StreamingTypeInfo;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.catalog.PluginScanner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceRoundTripTest {

    private final VoicePlatform platform = new ReferenceVoicePlatform();

    @Test
    void ttsProducesValidWavThatSttCanTranscribe() {
        SynthesisResult ttsResult = platform.textToSpeech()
                .synthesise("Hello world", new SynthesisOptions(null, "en", "wav", false));

        assertThat(ttsResult.audioData()).isNotEmpty();
        assertThat(ttsResult.audioFormat()).isEqualTo("wav");
        assertThat(ttsResult.audioData().length).isGreaterThan(44);

        byte[] wavHeader = new byte[4];
        System.arraycopy(ttsResult.audioData(), 0, wavHeader, 0, 4);
        assertThat(new String(wavHeader)).isEqualTo("RIFF");

        TranscriptionResult sttResult = platform.speechToText()
                .transcribe(ttsResult.audioData(), TranscriptionOptions.defaults());

        assertThat(sttResult.text()).isNotBlank();
        assertThat(sttResult.language()).isEqualTo("en");
        assertThat(sttResult.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void roundTripViaSttTranscribePluginDirect() {
        SynthesisResult ttsResult = platform.textToSpeech()
                .synthesise("Testing pipeline", new SynthesisOptions(null, "en", "wav", false));

        var plugin = new SttTranscribePlugin("en", null);
        TranscriptionResult result = plugin.apply(ttsResult.audioData(), platform);

        assertThat(result.text()).isNotBlank();
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.confidence()).isGreaterThan(0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void roundTripViaPluginScannerAndStreamingStep() {
        var scanner = new PluginScanner();
        var registry = new CompositePluginRegistry();
        scanner.scanAndRegister(SttTranscribePlugin.class, registry);

        var def = registry.resolve("stt-transcribe");
        assertThat(def).isPresent();
        assertThat(def.get().description()).isEqualTo("Transcribe audio bytes to text via VoicePlatform STT");
        assertThat(def.get().inputs()).containsKey("language");
        assertThat(def.get().inputs()).containsKey("model");
        assertThat(def.get().inputs().get("language").required()).isFalse();

        assertThat(def.get().streamingType()).isPresent();
        StreamingTypeInfo streamingInfo = def.get().streamingType().get();
        assertThat(streamingInfo.inputType()).isEqualTo(byte[].class);
        assertThat(streamingInfo.outputType()).isEqualTo(TranscriptionResult.class);

        var services = new MapServiceRegistry()
                .register(VoicePlatform.class, platform);

        StreamingStep<byte[], TranscriptionResult> step =
                (StreamingStep<byte[], TranscriptionResult>) streamingInfo.factory()
                        .create(Map.of("language", "en"), services);

        SynthesisResult ttsResult = platform.textToSpeech()
                .synthesise("Hello from YAML pipeline", new SynthesisOptions(null, "en", "wav", false));

        TranscriptionResult sttResult = step.apply(ttsResult.audioData());

        assertThat(sttResult.text()).isNotBlank();
        assertThat(sttResult.language()).isEqualTo("en");
        assertThat(sttResult.confidence()).isEqualTo(0.95);
    }

    @Test
    void platformServiceRegistersAndRoutes() {
        var ref = new ReferenceVoicePlatform();
        var noop = new NoOpVoicePlatform();
        var service = new VoicePlatformService(List.of(ref, noop));

        assertThat(service.ids()).containsExactlyInAnyOrder("ref", "none");
        assertThat(service.supports("ref")).isTrue();
        assertThat(service.supports("none")).isTrue();
        assertThat(service.supports("unknown")).isFalse();

        VoicePlatform resolved = service.platform("ref");
        assertThat(resolved.supports(SpeechToText.class)).isTrue();
        assertThat(resolved.supports(TextToSpeech.class)).isTrue();

        VoicePlatform resolvedNoop = service.platform("none");
        assertThat(resolvedNoop.supports(SpeechToText.class)).isFalse();
    }

    @Test
    void multipleRoundTripsProduceConsistentResults() {
        for (int i = 0; i < 5; i++) {
            SynthesisResult tts = platform.textToSpeech()
                    .synthesise("Iteration " + i, new SynthesisOptions(null, "en", "wav", false));
            TranscriptionResult stt = platform.speechToText()
                    .transcribe(tts.audioData(), TranscriptionOptions.defaults());

            assertThat(stt.text()).isNotBlank();
            assertThat(stt.confidence()).isEqualTo(0.95);
        }
    }
}
