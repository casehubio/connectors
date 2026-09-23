package io.casehub.connectors.bank;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.casehub.connectors.bank.spi.BankPlatform;

public class BankPlatformService {

    private final Map<String, BankPlatform> registry;

    public BankPlatformService(final List<BankPlatform> platforms) {
        this.registry = platforms.stream()
                .collect(Collectors.toMap(
                        BankPlatform::id,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate bank platform id: '" + a.id() + "'");
                        }));
    }

    public BankPlatform platform(final String id) {
        final BankPlatform platform = registry.get(id);
        if (platform == null) {
            throw new IllegalArgumentException(
                    "No bank platform registered for id '" + id
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
