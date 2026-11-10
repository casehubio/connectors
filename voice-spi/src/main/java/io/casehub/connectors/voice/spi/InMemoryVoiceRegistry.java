package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.VoiceCapability;
import io.casehub.connectors.voice.model.VoiceDescriptor;
import io.casehub.connectors.voice.model.VoiceQuery;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryVoiceRegistry implements MutableVoiceRegistry {

    private final ConcurrentHashMap<String, List<VoiceDescriptor>> sources = new ConcurrentHashMap<>();
    private volatile ConcurrentHashMap<String, VoiceDescriptor> view = new ConcurrentHashMap<>();

    private void rebuildView() {
        var merged = new ConcurrentHashMap<String, VoiceDescriptor>();
        sources.values().forEach(list -> list.forEach(v -> merged.put(v.id(), v)));
        this.view = merged;
    }

    @Override
    public Optional<VoiceDescriptor> resolveById(String voiceId) {
        return Optional.ofNullable(view.get(voiceId));
    }

    @Override
    public List<VoiceDescriptor> query(VoiceQuery query) {
        return view.values().stream()
            .filter(v -> query.vendor() == null || query.vendor().equals(v.vendor()))
            .filter(v -> query.gender() == null || query.gender().equals(v.gender()))
            .filter(v -> query.accent() == null || query.accent().equals(v.accent()))
            .filter(v -> query.minQuality() == null || v.quality() >= query.minQuality())
            .filter(v -> query.language() == null || v.languages().contains(query.language()))
            .filter(v -> query.style() == null || query.style().equals(v.style()))
            .filter(v -> query.capability() == null || v.capability() == query.capability()
                || v.capability() == VoiceCapability.BOTH)
            .toList();
    }

    @Override
    public List<VoiceDescriptor> all() {
        return List.copyOf(view.values());
    }

    @Override
    public CatalogDelta replaceSource(String sourceId, int priority, List<VoiceDescriptor> voices) {
        var oldIds = new HashSet<>(view.keySet());
        sources.put(sourceId, List.copyOf(voices));
        rebuildView();
        var newIds = new HashSet<>(view.keySet());
        var added = new HashSet<>(newIds);
        added.removeAll(oldIds);
        var removed = new HashSet<>(oldIds);
        removed.removeAll(newIds);
        return new CatalogDelta(added, removed, Set.of());
    }
}
