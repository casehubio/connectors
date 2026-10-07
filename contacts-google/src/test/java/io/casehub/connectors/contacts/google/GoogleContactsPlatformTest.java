package io.casehub.connectors.contacts.google;

import com.google.api.services.people.v1.model.Biography;
import com.google.api.services.people.v1.model.EmailAddress;
import com.google.api.services.people.v1.model.FieldMetadata;
import com.google.api.services.people.v1.model.Name;
import com.google.api.services.people.v1.model.Organization;
import com.google.api.services.people.v1.model.Person;
import com.google.api.services.people.v1.model.PhoneNumber;
import com.google.api.services.people.v1.model.Photo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleContactsPlatformTest {

    @Test
    void mapsPersonToContact() {
        var person = new Person()
            .setResourceName("people/123")
            .setNames(List.of(new Name()
                .setDisplayName("Alice Smith")
                .setGivenName("Alice")
                .setFamilyName("Smith")))
            .setEmailAddresses(List.of(
                new EmailAddress().setValue("alice@work.com").setType("work")
                    .setMetadata(new FieldMetadata().setPrimary(true)),
                new EmailAddress().setValue("alice@home.com").setType("home")))
            .setPhoneNumbers(List.of(
                new PhoneNumber().setValue("+1234567890").setType("mobile")
                    .setMetadata(new FieldMetadata().setPrimary(true))))
            .setOrganizations(List.of(
                new Organization().setName("Acme Corp").setTitle("Engineer")))
            .setPhotos(List.of(new Photo().setUrl("https://photo.url/alice")))
            .setBiographies(List.of(new Biography().setValue("A note")));

        var contact = GoogleContactsPlatform.mapContact(person);

        assertThat(contact.id()).isEqualTo("people/123");
        assertThat(contact.name().displayName()).isEqualTo("Alice Smith");
        assertThat(contact.name().givenName()).isEqualTo("Alice");
        assertThat(contact.name().familyName()).isEqualTo("Smith");
        assertThat(contact.emails()).hasSize(2);
        assertThat(contact.emails().getFirst().value()).isEqualTo("alice@work.com");
        assertThat(contact.emails().getFirst().primary()).isTrue();
        assertThat(contact.phones()).hasSize(1);
        assertThat(contact.phones().getFirst().label()).isEqualTo("mobile");
        assertThat(contact.company()).isEqualTo("Acme Corp");
        assertThat(contact.jobTitle()).isEqualTo("Engineer");
        assertThat(contact.photoUrl()).isEqualTo("https://photo.url/alice");
        assertThat(contact.notes()).isEqualTo("A note");
    }

    @Test
    void mapsPersonWithMinimalFields() {
        var person = new Person().setResourceName("people/456");

        var contact = GoogleContactsPlatform.mapContact(person);

        assertThat(contact.id()).isEqualTo("people/456");
        assertThat(contact.name().displayName()).isNull();
        assertThat(contact.emails()).isEmpty();
        assertThat(contact.phones()).isEmpty();
        assertThat(contact.addresses()).isEmpty();
        assertThat(contact.company()).isNull();
    }

    @Test
    void mapsAddresses() {
        var person = new Person()
            .setResourceName("people/789")
            .setAddresses(List.of(
                new com.google.api.services.people.v1.model.Address()
                    .setType("home")
                    .setStreetAddress("123 Main St")
                    .setCity("Springfield")
                    .setRegion("IL")
                    .setPostalCode("62701")
                    .setCountry("US")));

        var contact = GoogleContactsPlatform.mapContact(person);

        assertThat(contact.addresses()).hasSize(1);
        var addr = contact.addresses().getFirst();
        assertThat(addr.label()).isEqualTo("home");
        assertThat(addr.value().street()).isEqualTo("123 Main St");
        assertThat(addr.value().city()).isEqualTo("Springfield");
        assertThat(addr.value().country()).isEqualTo("US");
    }

    @Test
    void supportsAllCapabilities() {
        var provider = new io.casehub.platform.api.authn.ServiceConnectionProvider() {
            public io.casehub.platform.api.authn.ServiceConnection getConnection(String a, String p, String t) { return null; }
            public java.util.List<io.casehub.platform.api.authn.ServiceConnection> listConnections(String a, String t) { return java.util.List.of(); }
            public io.casehub.platform.api.authn.ServiceAccessToken getAccessToken(String a, String p, String t) { return null; }
            public java.util.Set<String> missingScopes(String a, String p, String t) { return java.util.Set.of(); }
        };
        var platform = new GoogleContactsPlatform(provider);
        assertThat(platform.id()).isEqualTo("google");
        assertThat(platform.supports(io.casehub.connectors.contacts.spi.ContactsPlatform.ContactRead.class)).isTrue();
        assertThat(platform.supports(io.casehub.connectors.contacts.spi.ContactsPlatform.GroupRead.class)).isTrue();
        assertThat(platform.supports(io.casehub.connectors.contacts.spi.ContactsPlatform.ContactWrite.class)).isTrue();
    }

    @Test
    void requiresScopes_annotationPresent() {
        var annotation = GoogleContactsPlatform.class.getAnnotation(
            io.casehub.platform.api.authn.RequiresScopes.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.provider()).isEqualTo("google");
    }
}
