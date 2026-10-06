package io.casehub.connectors.contacts.ref;

import io.casehub.connectors.contacts.model.Contact;
import io.casehub.connectors.contacts.model.Group;

import java.util.List;

public interface ContactsBackend {

    List<Contact> allContacts();

    Contact contact(String id);

    List<Contact> search(String query);

    List<Group> allGroups();

    List<Contact> contactsInGroup(String groupId);

    Contact create(Contact contact);

    Contact update(String id, Contact contact);

    void delete(String id);

    long currentVersion();

    List<Contact> changedSince(long version);

    List<String> deletedSince(long version);
}
