package io.casehub.connectors.graphql;

import io.casehub.connectors.voice.config.VoiceManifestDefaults;
import io.casehub.connectors.voice.spi.InMemoryVoiceRegistry;
import io.casehub.connectors.voice.spi.RoutingVoicePlatformService;
import io.casehub.connectors.voice.spi.VoicePlatformService;
import io.casehub.platform.api.mcp.ComponentStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceDomainReportProviderTest {

    @Test
    void report_includesAllPlatforms() {
        var tts = new ConnectorVoiceApiTest.StubVoicePlatform("kokoro", true, false);
        var stt = new ConnectorVoiceApiTest.StubVoicePlatform("whisper", false, true);
        var platformService = new VoicePlatformService(List.of(tts, stt));
        var registry = new InMemoryVoiceRegistry();
        var defaults = new VoiceManifestDefaults("kokoro", "whisper");
        var routingService = new RoutingVoicePlatformService(
                platformService, registry, Map.of(), defaults);

        var provider = new VoiceDomainReportProvider();
        provider.platformService = platformService;
        provider.routingService = routingService;
        provider.voiceRegistry = registry;

        var report = provider.report("connectors/voice");

        assertThat(report.domain()).isEqualTo("connectors/voice");
        assertThat(report.status()).isEqualTo(ComponentStatus.AVAILABLE);
        assertThat(report.providers()).containsKey("kokoro");
        assertThat(report.providers().get("kokoro").capabilities())
                .contains("TextToSpeech")
                .doesNotContain("SpeechToText");
        assertThat(report.providers()).containsKey("whisper");
        assertThat(report.providers().get("whisper").capabilities())
                .contains("SpeechToText")
                .doesNotContain("TextToSpeech");
        assertThat(report.metadata()).containsEntry("defaultTtsVendor", "kokoro");
        assertThat(report.metadata()).containsEntry("defaultSttVendor", "whisper");
        assertThat(report.metadata()).containsEntry("voiceCount", 0);
    }

    @Test
    void report_noPlatforms_statusUnavailable() {
        var platformService = new VoicePlatformService(List.of());
        var registry = new InMemoryVoiceRegistry();
        var routingService = new RoutingVoicePlatformService(
                platformService, registry, Map.of(), null);

        var provider = new VoiceDomainReportProvider();
        provider.platformService = platformService;
        provider.routingService = routingService;
        provider.voiceRegistry = registry;

        var report = provider.report("connectors/voice");

        assertThat(report.status()).isEqualTo(ComponentStatus.UNAVAILABLE);
        assertThat(report.providers()).isEmpty();
    }
}
