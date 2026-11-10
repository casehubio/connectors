package io.casehub.connectors.graphql;

import io.casehub.connectors.voice.spi.RoutingVoicePlatformService;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.connectors.voice.spi.VoicePlatformService;
import io.casehub.connectors.voice.spi.VoiceRegistry;
import io.casehub.platform.api.mcp.ComponentStatus;
import io.casehub.platform.api.mcp.DomainReport;
import io.casehub.platform.api.mcp.DomainReportProvider;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.ProviderReport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@McpDomain(value = "connectors/voice", app = "connectors",
    summary = "Voice connector — speech-to-text, text-to-speech, voice discovery")
@ApplicationScoped
public class VoiceDomainReportProvider implements DomainReportProvider {

    @Inject VoicePlatformService platformService;
    @Inject RoutingVoicePlatformService routingService;
    @Inject VoiceRegistry voiceRegistry;

    @Override
    public DomainReport report(String domain) {
        var providers = new LinkedHashMap<String, ProviderReport>();

        for (String id : platformService.ids()) {
            var platform = platformService.platform(id);
            var capabilities = new ArrayList<String>();
            if (platform.supports(TextToSpeech.class)) capabilities.add("TextToSpeech");
            if (platform.supports(SpeechToText.class)) capabilities.add("SpeechToText");
            providers.put(id, new ProviderReport(id, ComponentStatus.AVAILABLE,
                    capabilities, Map.of()));
        }

        ComponentStatus status = providers.isEmpty()
                ? ComponentStatus.UNAVAILABLE : ComponentStatus.AVAILABLE;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("voiceCount", voiceRegistry.all().size());
        try {
            metadata.put("defaultTtsVendor", routingService.defaultTts().id());
        } catch (IllegalStateException | IllegalArgumentException e) {
            metadata.put("defaultTtsVendor", null);
        }
        try {
            metadata.put("defaultSttVendor", routingService.defaultStt().id());
        } catch (IllegalStateException | IllegalArgumentException e) {
            metadata.put("defaultSttVendor", null);
        }

        return new DomainReport(domain, status, providers, metadata);
    }
}
