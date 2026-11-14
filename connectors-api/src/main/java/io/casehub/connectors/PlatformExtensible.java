package io.casehub.connectors;

import java.util.Optional;

public interface PlatformExtensible {
    default <T> Optional<T> as(Class<T> extensionType) {
        if (extensionType.isInstance(this)) {
            return Optional.of(extensionType.cast(this));
        }
        return Optional.empty();
    }
}
