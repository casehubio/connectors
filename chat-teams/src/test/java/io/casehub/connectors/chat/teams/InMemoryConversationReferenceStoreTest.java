package io.casehub.connectors.chat.teams;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryConversationReferenceStoreTest {

    private final InMemoryConversationReferenceStore store = new InMemoryConversationReferenceStore();

    @Test
    void find_unknownUser_returnsEmpty() {
        assertThat(store.find("unknown-aad-id")).isEmpty();
    }

    @Test
    void storeThenFind_returnsRef() {
        var ref = new ConversationReference("aad-1", "conv-1",
                "https://smba.trafficmanager.net/teams/", "tenant-1");
        store.store(ref);
        var found = store.find("aad-1");
        assertThat(found).isPresent();
        assertThat(found.get().conversationId()).isEqualTo("conv-1");
        assertThat(found.get().serviceUrl()).isEqualTo("https://smba.trafficmanager.net/teams/");
        assertThat(found.get().tenantId()).isEqualTo("tenant-1");
    }

    @Test
    void store_overwritesExistingRef() {
        var ref1 = new ConversationReference("aad-1", "conv-1",
                "https://service1.botframework.com/", "tenant-1");
        var ref2 = new ConversationReference("aad-1", "conv-2",
                "https://service2.botframework.com/", "tenant-1");
        store.store(ref1);
        store.store(ref2);
        var found = store.find("aad-1");
        assertThat(found).isPresent();
        assertThat(found.get().conversationId()).isEqualTo("conv-2");
    }
}
