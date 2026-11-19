package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.sherpa.correction.ConversationVocabulary;
import io.casehub.connectors.voice.sherpa.correction.NgramModel;
import io.casehub.connectors.voice.sherpa.correction.PhoneticIndex;
import io.casehub.connectors.voice.sherpa.correction.PhoneticStrategy;
import io.casehub.connectors.voice.sherpa.correction.SymSpellIndex;
import io.casehub.connectors.voice.sherpa.correction.SymSpellStrategy;
import io.casehub.connectors.voice.sherpa.correction.TranscriptCorrector;
import io.casehub.connectors.voice.spi.SpeakerDiarizationService;
import io.casehub.connectors.voice.spi.SpeakerEmbeddingExtractor;
import io.casehub.connectors.voice.spi.SpeakerRegistry;
import io.casehub.connectors.voice.spi.VoiceprintStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class SpeechInfrastructureBeans {

    private static final System.Logger LOG = System.getLogger(SpeechInfrastructureBeans.class.getName());

    @Produces
    @DefaultBean
    @ApplicationScoped
    SpeakerEmbeddingExtractor embeddingExtractor() {
        try {
            Path campplusPath = Provisioner.ensureCampplusModel();
            OnnxRuntimeLibrary.Session session =
                    OnnxRuntimeLibrary.load().createSession(campplusPath.resolve("campplus.onnx"), 2);
            return new CampplusSpeakerEmbeddingExtractor(session);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "CampplusSpeakerEmbeddingExtractor unavailable — native model not loaded: " + e.getMessage());
            return (samples, sampleRate) -> {
                throw new IllegalStateException("Speaker embedding extractor not available — campplus model failed to load");
            };
        }
    }

    @Produces
    @DefaultBean
    @Singleton
    VoiceprintStore voiceprintStore() {
        return new FileVoiceprintStore(
                Path.of(System.getProperty("user.home"), ".casehub", "voiceprints"));
    }

    @Produces
    @DefaultBean
    @Singleton
    SpeakerRegistry speakerRegistry(VoiceprintStore store) {
        return new CosineDistanceSpeakerRegistry(store);
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    SpeakerDiarizationService diarizationService() {
        try {
            Path segModelDir = Provisioner.ensureDiarizationModels();
            Path campplusDir = Provisioner.ensureCampplusModel();
            return new SherpaOnnxDiarizationService(
                    SherpaLibrary.load(),
                    segModelDir.resolve("model.onnx"),
                    campplusDir.resolve("campplus.onnx"));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "SpeakerDiarizationService unavailable — native models not loaded: " + e.getMessage());
            return (audioFile, options) -> List.of();
        }
    }

    @Produces
    @DefaultBean
    @Singleton
    ConversationVocabulary conversationVocabulary() {
        return new ConversationVocabulary();
    }

    @Produces
    @DefaultBean
    @Singleton
    TranscriptCorrector transcriptCorrector() {
        try {
            var symSpell = SymSpellIndex.fromResource("frequency_dictionary_en_82_765.txt");
            var phonetic = PhoneticIndex.fromSymSpellIndex(symSpell);
            var ngram = NgramModel.fromResource("frequency_bigramdictionary_en_243_342.txt");

            LOG.log(System.Logger.Level.INFO, "TranscriptCorrector loaded — {0} words",
                    symSpell.dictionary().size());
            return new TranscriptCorrector(
                    List.of(new SymSpellStrategy(symSpell), new PhoneticStrategy(phonetic)),
                    ngram, symSpell.dictionary());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "TranscriptCorrector unavailable — dictionary resources not found: " + e.getMessage());
            return new TranscriptCorrector(List.of(), null, Set.of());
        }
    }
}
