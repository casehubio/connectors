package io.casehub.connectors.contacts.ref;

import io.casehub.connectors.contacts.model.GroupType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadsThreeGroups() {
        var groups = SeedLoader.loadGroups();
        assertThat(groups).hasSize(3);
        assertThat(groups).extracting("name")
                .containsExactly("myContacts", "Starred", "Work");
    }

    @Test
    void groupTypesAreCorrect() {
        var groups = SeedLoader.loadGroups();
        assertThat(groups.getFirst().groupType()).isEqualTo(GroupType.SYSTEM);
        assertThat(groups.getLast().groupType()).isEqualTo(GroupType.USER_CREATED);
    }

    @Test
    void loadsTenContacts() {
        var contacts = SeedLoader.loadContacts();
        assertThat(contacts).hasSize(10);
    }

    @Test
    void firstContactHasCorrectFields() {
        var contacts = SeedLoader.loadContacts();
        var alice = contacts.getFirst();
        assertThat(alice.displayName()).isEqualTo("Alice Smith");
        assertThat(alice.givenName()).isEqualTo("Alice");
        assertThat(alice.email()).isEqualTo("alice@example.com");
        assertThat(alice.company()).isEqualTo("Acme Corp");
        assertThat(alice.groups()).containsExactly("g-mycontacts", "g-work");
    }
}
