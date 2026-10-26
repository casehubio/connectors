package io.casehub.connectors.contacts.ref;

import io.casehub.connectors.contacts.model.GroupType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadsFourGroups() {
        var groups = SeedLoader.loadGroups();
        assertThat(groups).hasSize(4);
        assertThat(groups).extracting("name")
                .containsExactly("Family", "Friends", "Professional", "Service Providers");
    }

    @Test
    void allGroupsAreUserCreated() {
        var groups = SeedLoader.loadGroups();
        assertThat(groups).allMatch(g -> g.groupType() == GroupType.USER_CREATED);
    }

    @Test
    void loadsHouseholdContacts() {
        var contacts = SeedLoader.loadContacts();
        assertThat(contacts).hasSizeGreaterThanOrEqualTo(28);
    }

    @Test
    void firstContactIsJonnyAlpha() {
        var contacts = SeedLoader.loadContacts();
        var jonny    = contacts.getFirst();
        assertThat(jonny.displayName()).isEqualTo("Jonny Alpha");
        assertThat(jonny.givenName()).isEqualTo("Jonny");
        assertThat(jonny.email()).isEqualTo("jonny.alpha@meridian-eng.co.uk");
        assertThat(jonny.company()).isEqualTo("Meridian Engineering");
        assertThat(jonny.groups()).containsExactly("g-family");
    }
}
