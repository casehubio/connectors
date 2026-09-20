package io.casehub.connectors;

import java.util.List;

public record Page<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> Page<T> of(List<T> items) {
        return new Page<>(items, null, false);
    }
}
