package io.casehub.connectors.graphql;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.contacts.model.Contact;
import io.casehub.connectors.contacts.model.ContactName;
import io.casehub.connectors.contacts.model.Group;
import io.casehub.connectors.contacts.model.GroupType;
import io.casehub.connectors.contacts.spi.ContactsPlatform;
import io.casehub.connectors.contacts.spi.ContactsPlatformService;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectorContactsApiTest {

    private ConnectorContactsApi api;
    private ContactsPlatform.ContactRead contactRead;
    private ContactsPlatform.GroupRead groupRead;
    private ContactsPlatform.ContactWrite contactWrite;

    @BeforeEach
    void setUp() {
        var platform = mock(ContactsPlatform.class);
        contactRead = mock(ContactsPlatform.ContactRead.class);
        groupRead = mock(ContactsPlatform.GroupRead.class);
        contactWrite = mock(ContactsPlatform.ContactWrite.class);

        when(platform.id()).thenReturn("test");
        when(platform.contactRead("test-user")).thenReturn(contactRead);
        when(platform.groupRead("test-user")).thenReturn(groupRead);
        when(platform.contactWrite("test-user")).thenReturn(contactWrite);
        when(platform.supports(ContactsPlatform.ContactRead.class)).thenReturn(true);
        when(platform.supports(ContactsPlatform.GroupRead.class)).thenReturn(true);
        when(platform.supports(ContactsPlatform.ContactWrite.class)).thenReturn(true);

        var identity = mock(SecurityIdentity.class);
        var principal = mock(Principal.class);
        when(identity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("test-user");

        api = new ConnectorContactsApi();
        api.platformService = new ContactsPlatformService(List.of(platform));
        api.identity = identity;
    }

    @Test
    void listContactsDelegatesToPlatform() {
        var contact = testContact("c1");
        when(contactRead.list(any(PageRequest.class)))
            .thenReturn(new Page<>(List.of(contact), null, false));

        var result = api.listContacts("test", null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().id()).isEqualTo("c1");
    }

    @Test
    void syncContactsDelegatesToPlatform() {
        when(contactRead.listSync(any()))
            .thenReturn(new SyncResult<>(List.of(), List.of("d1"), "token-2", false));

        var result = api.syncContacts("test", "token-1", 50);

        assertThat(result.deletedIds()).containsExactly("d1");
        assertThat(result.syncToken()).isEqualTo("token-2");
    }

    @Test
    void listGroupsDelegatesToPlatform() {
        when(groupRead.list()).thenReturn(List.of(
            new Group("g1", "Work", GroupType.USER_CREATED, 5)));

        var result = api.listGroups("test");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Work");
    }

    @Test
    void createContactDelegatesToPlatform() {
        var contact = testContact("c-new");
        when(contactWrite.create(any())).thenReturn(contact);

        var result = api.createContact("test", contact);

        assertThat(result.id()).isEqualTo("c-new");
    }

    private Contact testContact(String id) {
        return new Contact(id, new ContactName("Test", "Test", "User"),
            List.of(), List.of(), List.of(), null, null, null, null, Map.of());
    }
}
