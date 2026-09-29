package io.casehub.connectors.contacts.spi;

import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.contacts.model.Contact;
import io.casehub.connectors.contacts.model.Group;
import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "contacts-platform",
    capabilities = {"contactRead", "groupRead", "contactWrite"})
public interface ContactsPlatform {

    String id();

    boolean supports(Class<?> capability);

    ContactRead contactRead(String userId);

    GroupRead groupRead(String userId);

    ContactWrite contactWrite(String userId);

    interface ContactRead {

        Page<Contact> list(PageRequest pagination);

        SyncResult<Contact> listSync(SyncRequest request);

        Contact get(String contactId);

        Page<Contact> search(String query, PageRequest pagination);
    }

    interface GroupRead {

        List<Group> list();

        Page<Contact> listContacts(String groupId, PageRequest pagination);
    }

    interface ContactWrite {

        Contact create(Contact contact);

        Contact update(String contactId, Contact contact);

        void delete(String contactId);
    }
}
