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
import io.casehub.connectors.voice.spi.RoutingVoicePlatformService;
import io.casehub.connectors.voice.spi.VoicePlatformService;
import io.casehub.connectors.voice.steps.SttTranscribePlugin;
import io.casehub.connectors.voice.steps.TtsSynthesisePlugin;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.StreamingStep;
import io.casehub.yaml.plugin.api.StreamingTypeInfo;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import io.casehub.yaml.step.catalog.PluginScanner;
import io.casehub.yaml.step.catalog.ResolvedStep;
import io.casehub.yaml.step.catalog.Walker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceRoundTripTest {

    private final VoicePlatform               platform = new ReferenceVoicePlatform();
    private final RoutingVoicePlatformService routing  = buildRouting();

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

        var                 plugin = new SttTranscribePlugin("en", null);
        TranscriptionResult result = plugin.apply(ttsResult.audioData(), routing);

        assertThat(result.text()).isNotBlank();
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.confidence()).isGreaterThan(0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void roundTripViaPluginScannerAndStreamingStep() {
        var scanner  = new PluginScanner();
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
                               .register(RoutingVoicePlatformService.class, routing);

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
    @SuppressWarnings("unchecked")
    void yamlLoadedPipelineResolvesAndExecutesSttStep() throws Exception {
        var scanner  = new PluginScanner();
        var registry = new CompositePluginRegistry();
        scanner.scanAndRegister(SttTranscribePlugin.class, registry);

        var                 yaml = new org.yaml.snakeyaml.Yaml();
        Map<String, Object> doc;
        try (var stream = getClass().getResourceAsStream("/voice-message-pipeline.yaml")) {
            doc = yaml.load(stream);
        }
        var stepMaps = (List<Map<String, Object>>) doc.get("steps");
        assertThat(stepMaps).hasSize(1);

        var resolved = Walker.resolve(stepMaps, registry);
        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.PluginStep.class);

        var pluginStep = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(pluginStep.actionName()).isEqualTo("stt-transcribe");
        assertThat(pluginStep.name()).isEqualTo("transcribe");

        var definition = registry.resolve("stt-transcribe").orElseThrow();
        assertThat(definition.streamingType()).isPresent();

        var services = new MapServiceRegistry()
                               .register(RoutingVoicePlatformService.class, routing);

        StreamingStep<byte[], TranscriptionResult> step =
                (StreamingStep<byte[], TranscriptionResult>) definition.streamingType().get()
                                                                       .factory().create(pluginStep.params(), services);

        SynthesisResult ttsResult = platform.textToSpeech()
                                            .synthesise("YAML pipeline round-trip", new SynthesisOptions(null, "en", "wav", false));

        TranscriptionResult sttResult = step.apply(ttsResult.audioData());

        assertThat(sttResult.text()).isNotBlank();
        assertThat(sttResult.language()).isEqualTo("en");
        assertThat(sttResult.confidence()).isEqualTo(0.95);
    }

    @Test
    void platformServiceRegistersAndRoutes() {
        var ref     = new ReferenceVoicePlatform();
        var noop    = new NoOpVoicePlatform();
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

    @Test
    @SuppressWarnings("unchecked")
    void yamlRoundTripPipelineResolvesAndExecutesBothSteps() throws Exception {
        var scanner  = new PluginScanner();
        var registry = new CompositePluginRegistry();
        scanner.scanAndRegister(TtsSynthesisePlugin.class, registry);
        scanner.scanAndRegister(SttTranscribePlugin.class, registry);

        var ttsDefn = registry.resolve("tts-synthesise").orElseThrow();
        var sttDefn = registry.resolve("stt-transcribe").orElseThrow();
        assertThat(ttsDefn.streamingType()).isPresent();
        assertThat(sttDefn.streamingType()).isPresent();

        var                 yaml = new org.yaml.snakeyaml.Yaml();
        Map<String, Object> doc;
        try (var stream = getClass().getResourceAsStream("/voice-tts-stt-chain.yaml")) {
            doc = yaml.load(stream);
        }
        var stepMaps = (List<Map<String, Object>>) doc.get("steps");
        assertThat(stepMaps).hasSizeGreaterThanOrEqualTo(2);

        var resolved = Walker.resolve(stepMaps, registry);
        assertThat(resolved.stream()
                           .filter(s -> s instanceof ResolvedStep.PluginStep)
                           .map(s -> ((ResolvedStep.PluginStep) s).actionName())
                           .toList()).contains("tts-synthesise", "stt-transcribe");

        var fusedSteps = io.casehub.yaml.step.eval.FusionPass.fuse(resolved, registry);
        assertThat(fusedSteps).hasSizeGreaterThanOrEqualTo(2);

        var services = new MapServiceRegistry()
                               .register(RoutingVoicePlatformService.class, routing);

        StreamingStep<String, SynthesisResult> ttsStep =
                (StreamingStep<String, SynthesisResult>) ttsDefn.streamingType().get()
                                                                .factory().create(Map.of("language", "en"), services);

        SynthesisResult ttsResult = ttsStep.apply("Round-trip via YAML pipeline");
        assertThat(ttsResult.audioData()).isNotEmpty();
        assertThat(ttsResult.audioFormat()).isEqualTo("wav");

        StreamingStep<byte[], TranscriptionResult> sttStep =
                (StreamingStep<byte[], TranscriptionResult>) sttDefn.streamingType().get()
                                                                    .factory().create(Map.of(), services);

        TranscriptionResult sttResult = sttStep.apply(ttsResult.audioData());
        assertThat(sttResult.text()).isNotBlank();
        assertThat(sttResult.confidence()).isGreaterThan(0.0);
    }

    private RoutingVoicePlatformService buildRouting() {
        var platformService = new VoicePlatformService(List.of(platform));
        var registry        = new io.casehub.connectors.voice.spi.InMemoryVoiceRegistry();
        var loader          = new io.casehub.connectors.voice.config.VoiceManifestLoader();
        var manifest        = loader.load(null, null);
        var processor       = new io.casehub.connectors.voice.config.VoiceManifestProcessor(registry);
        var result          = processor.process(manifest);
        return new RoutingVoicePlatformService(
                platformService, registry, result.aliases(), manifest.defaults());
    }
}
