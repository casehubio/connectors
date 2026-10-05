package io.casehub.connectors.contacts.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ContactsRefBeans {

    @Produces
    @ApplicationScoped
    RefContactsPlatform refContactsPlatform(ContactsBackend backend) {
        return new RefContactsPlatform(backend);
    }
}
