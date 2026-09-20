package io.casehub.connectors.bank;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.casehub.connectors.bank.spi.BankFeedPlatform;

public class BankFeedPlatformService {

    private final Map<String, BankFeedPlatform> registry;

    public BankFeedPlatformService(final List<BankFeedPlatform> platforms) {
        this.registry = platforms.stream()
                .collect(Collectors.toMap(
                        BankFeedPlatform::id,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate bank feed platform id: '" + a.id() + "'");
                        }));
    }

    public BankFeedPlatform platform(final String id) {
        final BankFeedPlatform platform = registry.get(id);
        if (platform == null) {
            throw new IllegalArgumentException(
                    "No bank feed platform registered for id '" + id
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
