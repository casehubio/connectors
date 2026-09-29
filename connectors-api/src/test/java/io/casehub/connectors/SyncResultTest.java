package io.casehub.connectors;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyncResultTest {

    @Test
    void fullSyncResult() {
        var result = new SyncResult<>(
            List.of("a", "b"),
            List.of("deleted-1"),
            "next-token",
            false
        );
        assertThat(result.items()).containsExactly("a", "b");
        assertThat(result.deletedIds()).containsExactly("deleted-1");
        assertThat(result.syncToken()).isEqualTo("next-token");
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void emptySyncResult() {
        var result = new SyncResult<>(List.of(), List.of(), "token", true);
        assertThat(result.items()).isEmpty();
        assertThat(result.deletedIds()).isEmpty();
        assertThat(result.hasMore()).isTrue();
    }
}
