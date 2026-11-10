package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.VoiceDescriptor;
import java.util.List;
import java.util.Set;

public interface MutableVoiceRegistry extends VoiceRegistry {

    record CatalogDelta(Set<String> addedIds, Set<String> removedIds, Set<String> updatedIds) {
        public boolean hasChanges() {
            return !addedIds.isEmpty() || !removedIds.isEmpty() || !updatedIds.isEmpty();
        }
    }

    CatalogDelta replaceSource(String sourceId, int priority, List<VoiceDescriptor> voices);
}
