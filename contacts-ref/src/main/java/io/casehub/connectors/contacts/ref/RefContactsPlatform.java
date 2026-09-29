package io.casehub.connectors.contacts.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.contacts.model.Contact;
import io.casehub.connectors.contacts.model.Group;
import io.casehub.connectors.contacts.spi.ContactsPlatform;

import java.util.List;

public class RefContactsPlatform implements ContactsPlatform {

    private final ContactsBackend backend;

    public RefContactsPlatform(ContactsBackend backend) {
        this.backend = backend;
    }

    @Override
    public String id() {
        return "ref";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return capability == ContactRead.class
            || capability == GroupRead.class
            || capability == ContactWrite.class;
    }

    @Override
    public ContactRead contactRead(String userId) {
        return new RefContactRead();
    }

    @Override
    public GroupRead groupRead(String userId) {
        return new RefGroupRead();
    }

    @Override
    public ContactWrite contactWrite(String userId) {
        return new RefContactWrite();
    }

    private class RefContactRead implements ContactRead {

        @Override
        public Page<Contact> list(PageRequest pagination) {
            return paginate(backend.allContacts(), pagination);
        }

        @Override
        public SyncResult<Contact> listSync(SyncRequest request) {
            long sinceVersion = request.syncToken() != null
                ? Long.parseLong(request.syncToken()) : 0;
            var changed = backend.changedSince(sinceVersion);
            var deleted = backend.deletedSince(sinceVersion);
            return new SyncResult<>(changed, deleted,
                String.valueOf(backend.currentVersion()), false);
        }

        @Override
        public Contact get(String contactId) {
            return backend.contact(contactId);
        }

        @Override
        public Page<Contact> search(String query, PageRequest pagination) {
            return paginate(backend.search(query), pagination);
        }
    }

    private class RefGroupRead implements GroupRead {

        @Override
        public List<Group> list() {
            return backend.allGroups();
        }

        @Override
        public Page<Contact> listContacts(String groupId, PageRequest pagination) {
            return paginate(backend.contactsInGroup(groupId), pagination);
        }
    }

    private class RefContactWrite implements ContactWrite {

        @Override
        public Contact create(Contact contact) {
            return backend.create(contact);
        }

        @Override
        public Contact update(String contactId, Contact contact) {
            return backend.update(contactId, contact);
        }

        @Override
        public void delete(String contactId) {
            backend.delete(contactId);
        }
    }

    private static <T> Page<T> paginate(List<T> all, PageRequest pagination) {
        int start = 0;
        if (pagination.cursor() != null) {
            start = Integer.parseInt(pagination.cursor());
        }
        int size = pagination.pageSize() > 0 ? pagination.pageSize() : 20;
        int end = Math.min(start + size, all.size());
        var items = all.subList(start, end);
        boolean hasMore = end < all.size();
        String nextCursor = hasMore ? String.valueOf(end) : null;
        return new Page<>(items, nextCursor, hasMore);
    }
}
