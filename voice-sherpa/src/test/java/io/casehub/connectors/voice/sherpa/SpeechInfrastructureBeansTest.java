package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.sherpa.correction.ConversationVocabulary;
import io.casehub.connectors.voice.sherpa.correction.TranscriptCorrector;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechInfrastructureBeansTest {

    private final SpeechInfrastructureBeans beans = new SpeechInfrastructureBeans();

    @Test
    void voiceprintStoreCreatesFileStore() {
        var store = beans.voiceprintStore();
        assertThat(store).isInstanceOf(FileVoiceprintStore.class);
    }

    @Test
    void speakerRegistryCreatesCosineDistance() {
        var store = beans.voiceprintStore();
        var registry = beans.speakerRegistry(store);
        assertThat(registry).isInstanceOf(CosineDistanceSpeakerRegistry.class);
    }

    @Test
    void conversationVocabularyCreatesEmpty() {
        var vocab = beans.conversationVocabulary();
        assertThat(vocab).isNotNull();
        assertThat(vocab.terms()).isEmpty();
    }

    @Test
    void transcriptCorrectorLoads() {
        var corrector = beans.transcriptCorrector();
        assertThat(corrector).isNotNull();
        assertThat(corrector.correct("the quick brown fox")).isNotNull();
    }
}
