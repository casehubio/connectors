package io.casehub.connectors.contacts.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.ContactGroup;
import com.google.api.services.people.v1.model.EmailAddress;
import com.google.api.services.people.v1.model.Name;
import com.google.api.services.people.v1.model.Organization;
import com.google.api.services.people.v1.model.Person;
import com.google.api.services.people.v1.model.PhoneNumber;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.SyncTokenExpiredException;
import io.casehub.connectors.contacts.model.Address;
import io.casehub.connectors.contacts.model.Contact;
import io.casehub.connectors.contacts.model.ContactName;
import io.casehub.connectors.contacts.model.Group;
import io.casehub.connectors.contacts.model.GroupType;
import io.casehub.connectors.contacts.model.LabelledValue;
import io.casehub.connectors.contacts.spi.ContactsPlatform;
import io.casehub.platform.api.authn.RequiresScopes;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import org.jboss.logging.Logger;

import com.google.api.client.http.javanet.NetHttpTransport;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RequiresScopes(provider = "google",
    scopes = {"https://www.googleapis.com/auth/contacts.readonly",
              "https://www.googleapis.com/auth/contacts"})
public class GoogleContactsPlatform implements ContactsPlatform {

    private static final Logger LOG = Logger.getLogger(GoogleContactsPlatform.class);
    private static final String PERSON_FIELDS =
        "names,emailAddresses,phoneNumbers,addresses,organizations,photos,biographies,metadata";
    private static final String GROUP_FIELDS = "name,groupType,memberCount";
    private static final String DEFAULT_TENANCY = "default";

    private final ServiceConnectionProvider connectionProvider;
    private final NetHttpTransport transport;

    public GoogleContactsPlatform(ServiceConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        try {
            this.transport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize HTTP transport", e);
        }
    }

    @Override
    public String id() {
        return "google";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return capability == ContactRead.class
            || capability == GroupRead.class
            || capability == ContactWrite.class;
    }

    @Override
    public ContactRead contactRead(String userId) {
        return new GoogleContactRead(buildService(userId));
    }

    @Override
    public GroupRead groupRead(String userId) {
        return new GoogleGroupRead(buildService(userId));
    }

    @Override
    public ContactWrite contactWrite(String userId) {
        return new GoogleContactWrite(buildService(userId));
    }

    private PeopleService buildService(String actorId) {
        var token = connectionProvider.getAccessToken(actorId, "google", DEFAULT_TENANCY);
        var credentials = GoogleCredentials.create(
            new AccessToken(token.accessToken(), Date.from(token.expiresAt())));
        return new PeopleService.Builder(transport, GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
            .setApplicationName("casehub-connectors")
            .build();
    }

    private class GoogleContactRead implements ContactRead {

        private final PeopleService service;

        GoogleContactRead(PeopleService service) {
            this.service = service;
        }

        @Override
        public Page<Contact> list(PageRequest pagination) {
            try {
                var request = service.people().connections().list("people/me")
                    .setPersonFields(PERSON_FIELDS)
                    .setPageSize(pagination.pageSize() > 0 ? pagination.pageSize() : 20);
                if (pagination.cursor() != null) {
                    request.setPageToken(pagination.cursor());
                }
                var response = request.execute();
                var contacts = mapContacts(response.getConnections());
                return new Page<>(contacts, response.getNextPageToken(),
                    response.getNextPageToken() != null);
            } catch (IOException e) {
                LOG.warn("Partial results — Google People API list failed", e);
                return new Page<>(List.of(), null, false);
            }
        }

        @Override
        public SyncResult<Contact> listSync(SyncRequest request) {
            try {
                var req = service.people().connections().list("people/me")
                    .setPersonFields(PERSON_FIELDS)
                    .setRequestSyncToken(true)
                    .setPageSize(request.pageSize() > 0 ? request.pageSize() : 100);
                if (request.syncToken() != null) {
                    req.setSyncToken(request.syncToken());
                }
                var response = req.execute();
                var deletedIds = List.<String>of();
                var contacts = List.<Contact>of();
                if (response.getConnections() != null) {
                    deletedIds = response.getConnections().stream()
                        .filter(p -> p.getMetadata() != null
                            && Boolean.TRUE.equals(p.getMetadata().getDeleted()))
                        .map(Person::getResourceName)
                        .toList();
                    contacts = mapContacts(response.getConnections());
                }
                return new SyncResult<>(contacts, deletedIds,
                    response.getNextSyncToken(), response.getNextPageToken() != null);
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 410) {
                    throw new SyncTokenExpiredException(request.syncToken());
                }
                throw new RuntimeException("Google People API sync failed", e);
            } catch (IOException e) {
                throw new RuntimeException("Google People API sync failed", e);
            }
        }

        @Override
        public Contact get(String contactId) {
            try {
                var person = service.people().get(contactId)
                    .setPersonFields(PERSON_FIELDS)
                    .execute();
                return mapContact(person);
            } catch (IOException e) {
                throw new RuntimeException("Failed to get contact: " + contactId, e);
            }
        }

        @Override
        public Page<Contact> search(String query, PageRequest pagination) {
            try {
                var request = service.people().searchContacts()
                    .setQuery(query)
                    .setReadMask(PERSON_FIELDS)
                    .setPageSize(pagination.pageSize() > 0 ? pagination.pageSize() : 20);
                var response = request.execute();
                var contacts = response.getResults() == null ? List.<Contact>of()
                    : response.getResults().stream()
                        .map(r -> mapContact(r.getPerson()))
                        .toList();
                return new Page<>(contacts, null, false);
            } catch (IOException e) {
                LOG.warn("Partial results — Google People API search failed", e);
                return new Page<>(List.of(), null, false);
            }
        }
    }

    private class GoogleGroupRead implements GroupRead {

        private final PeopleService service;

        GoogleGroupRead(PeopleService service) {
            this.service = service;
        }

        @Override
        public List<Group> list() {
            try {
                var response = service.contactGroups().list()
                    .setGroupFields(GROUP_FIELDS)
                    .execute();
                if (response.getContactGroups() == null) return List.of();
                return response.getContactGroups().stream()
                    .map(this::mapGroup)
                    .toList();
            } catch (IOException e) {
                LOG.warn("Partial results — Google People API group list failed", e);
                return List.of();
            }
        }

        @Override
        public Page<Contact> listContacts(String groupId, PageRequest pagination) {
            try {
                var group = service.contactGroups().get(groupId)
                    .setGroupFields(GROUP_FIELDS)
                    .setMaxMembers(pagination.pageSize() > 0 ? pagination.pageSize() : 100)
                    .execute();
                if (group.getMemberResourceNames() == null) {
                    return new Page<>(List.of(), null, false);
                }
                var batchGet = service.people().getBatchGet()
                    .setResourceNames(group.getMemberResourceNames())
                    .setPersonFields(PERSON_FIELDS)
                    .execute();
                var contacts = batchGet.getResponses() == null ? List.<Contact>of()
                    : batchGet.getResponses().stream()
                        .map(r -> mapContact(r.getPerson()))
                        .toList();
                return new Page<>(contacts, null, false);
            } catch (IOException e) {
                LOG.warn("Partial results — Google People API group contacts failed", e);
                return new Page<>(List.of(), null, false);
            }
        }

        private Group mapGroup(ContactGroup cg) {
            var groupType = "SYSTEM_CONTACT_GROUP".equals(cg.getGroupType())
                ? GroupType.SYSTEM : GroupType.USER_CREATED;
            var memberCount = cg.getMemberCount() != null ? cg.getMemberCount() : 0;
            return new Group(cg.getResourceName(), cg.getName(), groupType, memberCount);
        }
    }

    private class GoogleContactWrite implements ContactWrite {

        private final PeopleService service;

        GoogleContactWrite(PeopleService service) {
            this.service = service;
        }

        @Override
        public Contact create(Contact contact) {
            try {
                var person = mapToPerson(contact);
                var created = service.people().createContact(person).execute();
                return mapContact(created);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create contact", e);
            }
        }

        @Override
        public Contact update(String contactId, Contact contact) {
            try {
                var existing = service.people().get(contactId)
                    .setPersonFields("metadata")
                    .execute();
                var person = mapToPerson(contact);
                person.setEtag(existing.getEtag());
                var updated = service.people().updateContact(contactId, person)
                    .setUpdatePersonFields(PERSON_FIELDS)
                    .execute();
                return mapContact(updated);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update contact: " + contactId, e);
            }
        }

        @Override
        public void delete(String contactId) {
            try {
                service.people().deleteContact(contactId).execute();
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete contact: " + contactId, e);
            }
        }

        private Person mapToPerson(Contact contact) {
            var person = new Person();
            if (contact.name() != null) {
                person.setNames(List.of(new Name()
                    .setGivenName(contact.name().givenName())
                    .setFamilyName(contact.name().familyName())
                    .setDisplayName(contact.name().displayName())));
            }
            if (contact.emails() != null && !contact.emails().isEmpty()) {
                person.setEmailAddresses(contact.emails().stream()
                    .map(e -> new EmailAddress().setValue(e.value()).setType(e.label()))
                    .toList());
            }
            if (contact.phones() != null && !contact.phones().isEmpty()) {
                person.setPhoneNumbers(contact.phones().stream()
                    .map(p -> new PhoneNumber().setValue(p.value()).setType(p.label()))
                    .toList());
            }
            if (contact.company() != null) {
                person.setOrganizations(List.of(new Organization()
                    .setName(contact.company())
                    .setTitle(contact.jobTitle())));
            }
            return person;
        }
    }

    private static List<Contact> mapContacts(List<Person> persons) {
        if (persons == null) return List.of();
        return persons.stream()
            .filter(p -> p.getMetadata() == null || !Boolean.TRUE.equals(p.getMetadata().getDeleted()))
            .map(GoogleContactsPlatform::mapContact)
            .toList();
    }

    static Contact mapContact(Person person) {
        var name = person.getNames() != null && !person.getNames().isEmpty()
            ? new ContactName(
                person.getNames().getFirst().getDisplayName(),
                person.getNames().getFirst().getGivenName(),
                person.getNames().getFirst().getFamilyName())
            : new ContactName(null, null, null);

        var emails = person.getEmailAddresses() == null ? List.<LabelledValue<String>>of()
            : person.getEmailAddresses().stream()
                .map(e -> new LabelledValue<>(e.getType(), e.getValue(),
                    e.getMetadata() != null && Boolean.TRUE.equals(e.getMetadata().getPrimary())))
                .toList();

        var phones = person.getPhoneNumbers() == null ? List.<LabelledValue<String>>of()
            : person.getPhoneNumbers().stream()
                .map(p -> new LabelledValue<>(p.getType(), p.getValue(),
                    p.getMetadata() != null && Boolean.TRUE.equals(p.getMetadata().getPrimary())))
                .toList();

        var addresses = person.getAddresses() == null ? List.<LabelledValue<Address>>of()
            : person.getAddresses().stream()
                .map(a -> new LabelledValue<>(a.getType(),
                    new Address(a.getStreetAddress(), a.getCity(), a.getRegion(),
                        a.getPostalCode(), a.getCountry()),
                    a.getMetadata() != null && Boolean.TRUE.equals(a.getMetadata().getPrimary())))
                .toList();

        var company = person.getOrganizations() != null && !person.getOrganizations().isEmpty()
            ? person.getOrganizations().getFirst().getName() : null;
        var jobTitle = person.getOrganizations() != null && !person.getOrganizations().isEmpty()
            ? person.getOrganizations().getFirst().getTitle() : null;

        var photoUrl = person.getPhotos() != null && !person.getPhotos().isEmpty()
            ? person.getPhotos().getFirst().getUrl() : null;

        var notes = person.getBiographies() != null && !person.getBiographies().isEmpty()
            ? person.getBiographies().getFirst().getValue() : null;

        return new Contact(person.getResourceName(), name, emails, phones, addresses,
            company, jobTitle, photoUrl, notes, Map.of());
    }
}
