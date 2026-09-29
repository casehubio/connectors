package io.casehub.connectors.contacts.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class ContactsRefBeans {

    @Produces
    @ApplicationScoped
    RefContactsPlatform refContactsPlatform() {
        return new RefContactsPlatform(new InMemoryContactsBackend());
    }
}
