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
    void firstContactIsMarkProctor() {
        var contacts = SeedLoader.loadContacts();
        var mark = contacts.getFirst();
        assertThat(mark.displayName()).isEqualTo("Mark Proctor");
        assertThat(mark.givenName()).isEqualTo("Mark");
        assertThat(mark.email()).isEqualTo("mark.proctor@meridian-eng.co.uk");
        assertThat(mark.company()).isEqualTo("Meridian Engineering");
        assertThat(mark.groups()).containsExactly("g-family");
    }
}
