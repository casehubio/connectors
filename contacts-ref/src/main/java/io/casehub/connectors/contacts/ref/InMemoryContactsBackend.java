package io.casehub.connectors.contacts.ref;

import io.casehub.connectors.contacts.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class InMemoryContactsBackend implements ContactsBackend {

    private final Map<String, VersionedContact> contacts = new ConcurrentHashMap<>();
    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> groupMembers = new ConcurrentHashMap<>();
    private final Map<String, Long> deletedVersions = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong(0);
    private final AtomicLong idSeq = new AtomicLong(0);

    InMemoryContactsBackend() {
        seed();
    }

    private void seed() {
        groups.put("g-mycontacts", new Group("g-mycontacts", "myContacts", GroupType.SYSTEM, 0));
        groups.put("g-starred", new Group("g-starred", "Starred", GroupType.SYSTEM, 0));
        groups.put("g-work", new Group("g-work", "Work", GroupType.USER_CREATED, 0));
        groupMembers.put("g-mycontacts", ConcurrentHashMap.newKeySet());
        groupMembers.put("g-starred", ConcurrentHashMap.newKeySet());
        groupMembers.put("g-work", ConcurrentHashMap.newKeySet());

        addSeed("Alice Smith", "Alice", "Smith", "alice@example.com", "+1234567001", "Acme Corp", "Engineer", "g-mycontacts", "g-work");
        addSeed("Bob Jones", "Bob", "Jones", "bob@example.com", "+1234567002", "Acme Corp", "Manager", "g-mycontacts", "g-work");
        addSeed("Carol Smith", "Carol", "Smith", "carol@example.com", "+1234567003", "Beta Inc", "Designer", "g-mycontacts", "g-starred");
        addSeed("Dave Wilson", "Dave", "Wilson", "dave@example.com", "+1234567004", "Beta Inc", "CEO", "g-mycontacts");
        addSeed("Eve Brown", "Eve", "Brown", "eve@example.com", "+1234567005", "Gamma LLC", "CTO", "g-mycontacts", "g-work");
        addSeed("Frank Lee", "Frank", "Lee", "frank@example.com", "+1234567006", "Gamma LLC", "Intern", "g-mycontacts");
        addSeed("Grace Chen", "Grace", "Chen", "grace@example.com", "+1234567007", "Delta Co", "VP", "g-mycontacts", "g-starred");
        addSeed("Hank Miller", "Hank", "Miller", "hank@example.com", "+1234567008", "Acme Corp", "Analyst", "g-mycontacts", "g-work");
        addSeed("Ivy Davis", "Ivy", "Davis", "ivy@example.com", "+1234567009", "Beta Inc", "PM", "g-mycontacts");
        addSeed("Jack Taylor", "Jack", "Taylor", "jack@example.com", "+1234567010", "Delta Co", "Director", "g-mycontacts", "g-starred");

        updateGroupCounts();
    }

    private void addSeed(String display, String given, String family, String email, String phone,
                         String company, String title, String... memberOfGroups) {
        var id = "c-" + idSeq.incrementAndGet();
        var contact = new Contact(id,
            new ContactName(display, given, family),
            List.of(new LabelledValue<>("work", email, true)),
            List.of(new LabelledValue<>("mobile", phone, true)),
            List.of(),
            company, title, null, null, Map.of());
        contacts.put(id, new VersionedContact(contact, version.incrementAndGet()));
        for (var g : memberOfGroups) {
            groupMembers.get(g).add(id);
        }
    }

    private void updateGroupCounts() {
        groupMembers.forEach((gId, members) -> {
            var g = groups.get(gId);
            groups.put(gId, new Group(g.id(), g.name(), g.groupType(), members.size()));
        });
    }

    @Override
    public List<Contact> allContacts() {
        return contacts.values().stream().map(VersionedContact::contact).toList();
    }

    @Override
    public Contact contact(String id) {
        var vc = contacts.get(id);
        if (vc == null) throw new NoSuchElementException("Contact not found: " + id);
        return vc.contact();
    }

    @Override
    public List<Contact> search(String query) {
        var q = query.toLowerCase();
        return contacts.values().stream()
            .map(VersionedContact::contact)
            .filter(c -> matchesQuery(c, q))
            .toList();
    }

    private boolean matchesQuery(Contact c, String query) {
        if (c.name() != null && c.name().displayName() != null
                && c.name().displayName().toLowerCase().contains(query)) return true;
        if (c.company() != null && c.company().toLowerCase().contains(query)) return true;
        return c.emails().stream().anyMatch(e -> e.value().toLowerCase().contains(query));
    }

    @Override
    public List<Group> allGroups() {
        return List.copyOf(groups.values());
    }

    @Override
    public List<Contact> contactsInGroup(String groupId) {
        var members = groupMembers.get(groupId);
        if (members == null) throw new NoSuchElementException("Group not found: " + groupId);
        return members.stream()
            .map(id -> contacts.get(id))
            .filter(Objects::nonNull)
            .map(VersionedContact::contact)
            .toList();
    }

    @Override
    public Contact create(Contact contact) {
        var id = "c-" + idSeq.incrementAndGet();
        var created = new Contact(id, contact.name(), contact.emails(), contact.phones(),
            contact.addresses(), contact.company(), contact.jobTitle(),
            contact.photoUrl(), contact.notes(), contact.metadata());
        contacts.put(id, new VersionedContact(created, version.incrementAndGet()));
        groupMembers.get("g-mycontacts").add(id);
        updateGroupCounts();
        return created;
    }

    @Override
    public Contact update(String id, Contact contact) {
        if (!contacts.containsKey(id)) throw new NoSuchElementException("Contact not found: " + id);
        var updated = new Contact(id, contact.name(), contact.emails(), contact.phones(),
            contact.addresses(), contact.company(), contact.jobTitle(),
            contact.photoUrl(), contact.notes(), contact.metadata());
        contacts.put(id, new VersionedContact(updated, version.incrementAndGet()));
        return updated;
    }

    @Override
    public void delete(String id) {
        if (contacts.remove(id) == null) throw new NoSuchElementException("Contact not found: " + id);
        deletedVersions.put(id, version.incrementAndGet());
        groupMembers.values().forEach(members -> members.remove(id));
        updateGroupCounts();
    }

    @Override
    public long currentVersion() {
        return version.get();
    }

    @Override
    public List<Contact> changedSince(long sinceVersion) {
        return contacts.values().stream()
            .filter(vc -> vc.version() > sinceVersion)
            .map(VersionedContact::contact)
            .toList();
    }

    @Override
    public List<String> deletedSince(long sinceVersion) {
        return deletedVersions.entrySet().stream()
            .filter(e -> e.getValue() > sinceVersion)
            .map(Map.Entry::getKey)
            .toList();
    }

    private record VersionedContact(Contact contact, long version) {}
}
