package io.casehub.connectors.email.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EmailPlatformService {

    private final Map<String, EmailPlatform> registry;

    public EmailPlatformService(final List<EmailPlatform> platforms) {
        this.registry = platforms.stream()
                .collect(Collectors.toMap(
                        EmailPlatform::id,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate email platform id: '" + a.id() + "'");
                        }));
    }

    public EmailPlatform platform(final String id) {
        final EmailPlatform platform = registry.get(id);
        if (platform == null) {
            throw new IllegalArgumentException(
                    "No email platform registered for id '" + id
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
