package io.casehub.connectors;

import java.util.List;

public record SyncResult<T>(
    List<T> items,
    List<String> deletedIds,
    String syncToken,
    boolean hasMore
) {}
