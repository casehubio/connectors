package io.casehub.connectors.contacts.spi;

import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.contacts.model.Contact;
import io.casehub.connectors.contacts.model.Group;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpContactsPlatform implements ContactsPlatform {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return false;
    }

    @Override
    public ContactRead contactRead(String userId) {
        return NoOpContactRead.INSTANCE;
    }

    @Override
    public GroupRead groupRead(String userId) {
        return NoOpGroupRead.INSTANCE;
    }

    @Override
    public ContactWrite contactWrite(String userId) {
        return NoOpContactWrite.INSTANCE;
    }

    private enum NoOpContactRead implements ContactRead {
        INSTANCE;

        @Override
        public Page<Contact> list(PageRequest pagination) {
            throw new UnsupportedCapabilityException("list", "ContactRead", "none", List.of());
        }

        @Override
        public SyncResult<Contact> listSync(SyncRequest request) {
            throw new UnsupportedCapabilityException("listSync", "ContactRead", "none", List.of());
        }

        @Override
        public Contact get(String contactId) {
            throw new UnsupportedCapabilityException("get", "ContactRead", "none", List.of());
        }

        @Override
        public Page<Contact> search(String query, PageRequest pagination) {
            throw new UnsupportedCapabilityException("search", "ContactRead", "none", List.of());
        }
    }

    private enum NoOpGroupRead implements GroupRead {
        INSTANCE;

        @Override
        public List<Group> list() {
            throw new UnsupportedCapabilityException("list", "GroupRead", "none", List.of());
        }

        @Override
        public Page<Contact> listContacts(String groupId, PageRequest pagination) {
            throw new UnsupportedCapabilityException("listContacts", "GroupRead", "none", List.of());
        }
    }

    private enum NoOpContactWrite implements ContactWrite {
        INSTANCE;

        @Override
        public Contact create(Contact contact) {
            throw new UnsupportedCapabilityException("create", "ContactWrite", "none", List.of());
        }

        @Override
        public Contact update(String contactId, Contact contact) {
            throw new UnsupportedCapabilityException("update", "ContactWrite", "none", List.of());
        }

        @Override
        public void delete(String contactId) {
            throw new UnsupportedCapabilityException("delete", "ContactWrite", "none", List.of());
        }
    }
}
