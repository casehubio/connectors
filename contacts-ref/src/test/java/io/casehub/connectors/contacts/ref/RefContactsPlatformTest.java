package io.casehub.connectors.contacts.ref;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.contacts.model.Contact;
import io.casehub.connectors.contacts.model.ContactName;
import io.casehub.connectors.contacts.model.GroupType;
import io.casehub.connectors.contacts.spi.ContactsPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefContactsPlatformTest {

    private RefContactsPlatform platform;

    @BeforeEach
    void setUp() {
        platform = new RefContactsPlatform(new InMemoryContactsBackend());
    }

    @Test
    void id() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void supportsAllCapabilities() {
        assertThat(platform.supports(ContactsPlatform.ContactRead.class)).isTrue();
        assertThat(platform.supports(ContactsPlatform.GroupRead.class)).isTrue();
        assertThat(platform.supports(ContactsPlatform.ContactWrite.class)).isTrue();
    }

    @Test
    void listContactsReturnsPaginatedResults() {
        var page = platform.contactRead("user1").list(new PageRequest(null, 5));
        assertThat(page.items()).hasSize(5);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotNull();
    }

    @Test
    void listContactsPaginates() {
        var page1 = platform.contactRead("user1").list(new PageRequest(null, 5));
        var page2 = platform.contactRead("user1").list(new PageRequest(page1.nextCursor(), 5));
        assertThat(page2.items()).hasSize(5);
        assertThat(page2.hasMore()).isFalse();
    }

    @Test
    void getContactById() {
        var contacts = platform.contactRead("user1").list(new PageRequest(null, 10));
        var first = contacts.items().getFirst();
        var retrieved = platform.contactRead("user1").get(first.id());
        assertThat(retrieved).isEqualTo(first);
    }

    @Test
    void searchContactsByName() {
        var results = platform.contactRead("user1").search("Smith", new PageRequest(null, 10));
        assertThat(results.items()).isNotEmpty();
        assertThat(results.items()).allSatisfy(c ->
            assertThat(c.name().displayName().toLowerCase()).contains("smith")
        );
    }

    @Test
    void listGroups() {
        var groups = platform.groupRead("user1").list();
        assertThat(groups).isNotEmpty();
        assertThat(groups).anyMatch(g -> g.groupType() == GroupType.SYSTEM);
        assertThat(groups).anyMatch(g -> g.groupType() == GroupType.USER_CREATED);
    }

    @Test
    void listContactsInGroup() {
        var groups = platform.groupRead("user1").list();
        var workGroup = groups.stream()
            .filter(g -> g.groupType() == GroupType.USER_CREATED)
            .findFirst().orElseThrow();
        var page = platform.groupRead("user1").listContacts(workGroup.id(), new PageRequest(null, 20));
        assertThat(page.items()).isNotEmpty();
        assertThat(page.items().size()).isEqualTo(workGroup.memberCount());
    }

    @Test
    void fullSyncThenIncrementalSync() {
        var fullSync = platform.contactRead("user1").listSync(SyncRequest.initial(100));
        assertThat(fullSync.items()).hasSize(10);
        assertThat(fullSync.syncToken()).isNotNull();
        assertThat(fullSync.deletedIds()).isEmpty();

        var incrementalSync = platform.contactRead("user1").listSync(
            new SyncRequest(fullSync.syncToken(), 100));
        assertThat(incrementalSync.items()).isEmpty();
        assertThat(incrementalSync.deletedIds()).isEmpty();
    }

    @Test
    void syncDetectsChangesAfterCreate() {
        var fullSync = platform.contactRead("user1").listSync(SyncRequest.initial(100));
        var token = fullSync.syncToken();

        var created = platform.contactWrite("user1").create(
            new Contact(null, new ContactName("New Person", "New", "Person"),
                List.of(), List.of(), List.of(), null, null, null, null, Map.of()));

        var incrementalSync = platform.contactRead("user1").listSync(new SyncRequest(token, 100));
        assertThat(incrementalSync.items()).hasSize(1);
        assertThat(incrementalSync.items().getFirst().id()).isEqualTo(created.id());
    }

    @Test
    void syncDetectsDeletes() {
        var contacts = platform.contactRead("user1").list(new PageRequest(null, 10));
        var toDelete = contacts.items().getFirst();

        var fullSync = platform.contactRead("user1").listSync(SyncRequest.initial(100));
        var token = fullSync.syncToken();

        platform.contactWrite("user1").delete(toDelete.id());

        var incrementalSync = platform.contactRead("user1").listSync(new SyncRequest(token, 100));
        assertThat(incrementalSync.deletedIds()).contains(toDelete.id());
    }

    @Test
    void createUpdateDeleteContact() {
        var contact = new Contact(null, new ContactName("Test User", "Test", "User"),
            List.of(), List.of(), List.of(), "TestCo", "Engineer", null, null, Map.of());

        var created = platform.contactWrite("user1").create(contact);
        assertThat(created.id()).isNotNull();
        assertThat(created.name().displayName()).isEqualTo("Test User");

        var updated = platform.contactWrite("user1").update(created.id(),
            new Contact(created.id(), new ContactName("Updated User", "Updated", "User"),
                List.of(), List.of(), List.of(), "TestCo", "Senior Engineer", null, null, Map.of()));
        assertThat(updated.name().displayName()).isEqualTo("Updated User");
        assertThat(updated.jobTitle()).isEqualTo("Senior Engineer");

        platform.contactWrite("user1").delete(created.id());
        assertThatThrownBy(() -> platform.contactRead("user1").get(created.id()))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
