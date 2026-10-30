package io.casehub.connectors.email.google.inbound;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryHistoryStoreTest {

    @Test
    void emptyOnStart() {
        var store = new InMemoryHistoryStore();
        assertThat(store.getLastHistoryId("user@example.com")).isEmpty();
    }

    @Test
    void setAndGet() {
        var store = new InMemoryHistoryStore();
        store.setLastHistoryId("user@example.com", 12345L);
        assertThat(store.getLastHistoryId("user@example.com")).hasValue(12345L);
    }

    @Test
    void perUserIsolation() {
        var store = new InMemoryHistoryStore();
        store.setLastHistoryId("a@example.com", 100L);
        store.setLastHistoryId("b@example.com", 200L);
        assertThat(store.getLastHistoryId("a@example.com")).hasValue(100L);
        assertThat(store.getLastHistoryId("b@example.com")).hasValue(200L);
    }
}
