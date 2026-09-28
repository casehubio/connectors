package io.casehub.connectors.document.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DocumentPlatformService {

    private final Map<String, DocumentPlatform> registry;

    public DocumentPlatformService(final List<DocumentPlatform> platforms) {
        this.registry = platforms.stream()
                .collect(Collectors.toMap(
                        DocumentPlatform::id,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate document platform id: '" + a.id() + "'");
                        }));
    }

    public DocumentPlatform platform(final String id) {
        final DocumentPlatform platform = registry.get(id);
        if (platform == null) {
            throw new IllegalArgumentException(
                    "No document platform registered for id '" + id
                    + "'. Available: " + registry.keySet());
        }
        return platform;
    }

    public boolean supports(final String id) {
        return registry.containsKey(id);
    }

    public Set<String> ids() {
        return Set.copyOf(registry.keySet());
    }
}
