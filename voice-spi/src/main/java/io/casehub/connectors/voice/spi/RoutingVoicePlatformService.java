package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.config.VoiceAliasDeclaration;
import io.casehub.connectors.voice.config.VoiceManifestDefaults;
import io.casehub.connectors.voice.model.VoiceDescriptor;
import io.casehub.connectors.voice.model.VoiceQuery;
import org.jboss.logging.Logger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class RoutingVoicePlatformService {

    private static final Logger LOG = Logger.getLogger(RoutingVoicePlatformService.class);

    private final VoicePlatformService platformService;
    private final VoiceRegistry voiceRegistry;
    private final Map<String, VoiceAliasDeclaration> aliases;
    private final VoiceManifestDefaults defaults;

    public RoutingVoicePlatformService(VoicePlatformService platformService,
                                       VoiceRegistry voiceRegistry,
                                       Map<String, VoiceAliasDeclaration> aliases,
                                       VoiceManifestDefaults defaults) {
        this.platformService = platformService;
        this.voiceRegistry = voiceRegistry;
        this.aliases = aliases != null ? Map.copyOf(aliases) : Map.of();
        this.defaults = defaults;
        LOG.infof("Voice router initialized: %d voices, %d aliases, tts=%s, stt=%s",
            voiceRegistry.all().size(), this.aliases.size(),
            defaults != null ? defaults.ttsVendor() : "(none)",
            defaults != null ? defaults.sttVendor() : "(none)");
    }

    public ResolvedVoice resolve(String voiceRef) {return resolve(voiceRef, new java.util.HashSet<>());}

    private ResolvedVoice resolve(String voiceRef, java.util.Set<String> visited) {
        if (aliases.containsKey(voiceRef)) {
            if (!visited.add(voiceRef)) {
                throw new IllegalArgumentException("Alias cycle detected: " + visited);
            }
            var alias = aliases.get(voiceRef);
            if (alias.voice() != null) {
                return resolve(alias.voice(), visited);
            }
            return resolveQuery(toVoiceQuery(alias));
        }
        var descriptor = voiceRegistry.resolveById(voiceRef);
        if (descriptor.isPresent()) {
            var d        = descriptor.get();
            var platform = platformService.platform(d.vendor());
            return new ResolvedVoice(platform, d);
        }
        if (platformService.supports(voiceRef)) {
            return new ResolvedVoice(platformService.platform(voiceRef), null);
        }
        throw new IllegalArgumentException("No voice or alias: " + voiceRef);
    }


    public ResolvedVoice resolveQuery(VoiceQuery query) {
        var candidates = voiceRegistry.query(query);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No voice matching query: " + query);
        }
        var selected = selectBest(candidates, query.preferVendor());
        var platform = platformService.platform(selected.vendor());
        return new ResolvedVoice(platform, selected);
    }

    public VoicePlatform defaultTts() {
        if (defaults == null || defaults.ttsVendor() == null) {
            throw new IllegalStateException("No default TTS vendor configured");
        }
        return platformService.platform(defaults.ttsVendor());
    }

    public VoicePlatform defaultStt() {
        if (defaults == null || defaults.sttVendor() == null) {
            throw new IllegalStateException("No default STT vendor configured");
        }
        return platformService.platform(defaults.sttVendor());
    }

    public VoiceRegistry voiceRegistry() {
        return voiceRegistry;
    }

    private VoiceDescriptor selectBest(List<VoiceDescriptor> candidates, String preferVendor) {
        if (preferVendor != null) {
            var preferred = candidates.stream()
                .filter(v -> v.vendor().equals(preferVendor))
                .findFirst();
            if (preferred.isPresent()) return preferred.get();
        }
        return candidates.stream()
            .max(Comparator.comparingDouble(VoiceDescriptor::quality))
            .orElse(candidates.get(0));
    }

    private static VoiceQuery toVoiceQuery(VoiceAliasDeclaration alias) {
        return VoiceQuery.builder()
            .minQuality(alias.minQuality())
            .preferVendor(alias.preferVendor())
            .gender(alias.gender())
            .accent(alias.accent())
            .language(alias.language())
            .style(alias.style())
            .capability(alias.capability())
            .build();
    }
}
