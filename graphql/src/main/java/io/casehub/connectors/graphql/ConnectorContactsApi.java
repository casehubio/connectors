package io.casehub.connectors.graphql;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.contacts.model.Contact;
import io.casehub.connectors.contacts.model.Group;
import io.casehub.connectors.contacts.spi.ContactsPlatform;
import io.casehub.connectors.contacts.spi.ContactsPlatformService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.util.ArrayList;
import java.util.List;

@McpDomain(value = "connectors/contacts", app = "connectors",
    basePath = "/api/connectors/contacts",
    summary = "Contacts connector — contacts, groups, sync")
@ApplicationScoped
public class ConnectorContactsApi {

    @Inject ContactsPlatformService platformService;
    @Inject SecurityIdentity identity;

    private String userId() {
        return identity.getPrincipal().getName();
    }

    @PlatformQuery("List contacts from a provider")
    @RestPath("/contacts")
    public Page<Contact> listContacts(
            @QueryParam("platform") String platformId,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        var p = platformService.platform(platformId);
        requireCapability(p, ContactsPlatform.ContactRead.class, "listContacts");
        int size = pageSize != null ? pageSize : 20;
        return p.contactRead(userId()).list(new PageRequest(cursor, size));
    }

    @PlatformQuery("Incremental sync of contacts")
    @RestPath("/contacts/sync")
    public SyncResult<Contact> syncContacts(
            @QueryParam("platform") String platformId,
            @QueryParam("syncToken") String syncToken,
            @QueryParam("pageSize") Integer pageSize) {
        var p = platformService.platform(platformId);
        requireCapability(p, ContactsPlatform.ContactRead.class, "syncContacts");
        int size = pageSize != null ? pageSize : 100;
        var request = syncToken != null
            ? new SyncRequest(syncToken, size)
            : SyncRequest.initial(size);
        return p.contactRead(userId()).listSync(request);
    }

    @PlatformQuery("Get a specific contact by ID")
    @RestPath("/contacts/{contactId}")
    public Contact getContact(
            @QueryParam("platform") String platformId,
            @PathParam String contactId) {
        var p = platformService.platform(platformId);
        requireCapability(p, ContactsPlatform.ContactRead.class, "getContact");
        return p.contactRead(userId()).get(contactId);
    }

    @PlatformQuery("Search contacts by query")
    @RestPath("/contacts/search")
    public Page<Contact> searchContacts(
            @QueryParam("platform") String platformId,
            @QueryParam("query") String query,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        var p = platformService.platform(platformId);
        requireCapability(p, ContactsPlatform.ContactRead.class, "searchContacts");
        int size = pageSize != null ? pageSize : 20;
        return p.contactRead(userId()).search(query, new PageRequest(cursor, size));
    }

    @PlatformQuery("List contact groups")
    @RestPath("/groups")
    public List<Group> listGroups(@QueryParam("platform") String platformId) {
        var p = platformService.platform(platformId);
        requireCapability(p, ContactsPlatform.GroupRead.class, "listGroups");
        return p.groupRead(userId()).list();
    }

    @PlatformQuery("List contacts in a group")
    @RestPath("/groups/{groupId}/contacts")
    public Page<Contact> listGroupContacts(
            @QueryParam("platform") String platformId,
            @PathParam String groupId,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        var p = platformService.platform(platformId);
        requireCapability(p, ContactsPlatform.GroupRead.class, "listGroupContacts");
        int size = pageSize != null ? pageSize : 20;
        return p.groupRead(userId()).listContacts(groupId, new PageRequest(cursor, size));
    }

    @PlatformMutation("Create a new contact")
    @RestPath("/contacts")
    public Contact createContact(
            @QueryParam("platform") String platformId,
            Contact contact) {
        var p = platformService.platform(platformId);
        requireCapability(p, ContactsPlatform.ContactWrite.class, "createContact");
        return p.contactWrite(userId()).create(contact);
    }

    @PlatformMutation("Update an existing contact")
    @RestPath("/contacts/{contactId}")
    public Contact updateContact(
            @QueryParam("platform") String platformId,
            @PathParam String contactId,
            Contact contact) {
        var p = platformService.platform(platformId);
        requireCapability(p, ContactsPlatform.ContactWrite.class, "updateContact");
        return p.contactWrite(userId()).update(contactId, contact);
    }

    @PlatformMutation("Delete a contact")
    @RestPath("/contacts/{contactId}/delete")
    public void deleteContact(
            @QueryParam("platform") String platformId,
            @PathParam String contactId) {
        var p = platformService.platform(platformId);
        requireCapability(p, ContactsPlatform.ContactWrite.class, "deleteContact");
        p.contactWrite(userId()).delete(contactId);
    }

    private static void requireCapability(ContactsPlatform platform, Class<?> capability, String operation) {
        if (!platform.supports(capability)) {
            var supported = new ArrayList<String>();
            if (platform.supports(ContactsPlatform.ContactRead.class)) {supported.add("ContactRead");}
            if (platform.supports(ContactsPlatform.GroupRead.class)) {supported.add("GroupRead");}
            if (platform.supports(ContactsPlatform.ContactWrite.class)) {supported.add("ContactWrite");}
            throw new UnsupportedCapabilityException(
                    operation, capability.getSimpleName(), platform.id(), supported);
        }
    }
}
