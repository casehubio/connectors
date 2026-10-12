package io.casehub.connectors.contacts.spi;

import io.casehub.platform.api.confirmation.RequiresConfirmation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequiresConfirmationTest {

    @Test
    void contactDeleteRequiresConfirmation() throws Exception {
        var method = ContactsPlatform.ContactWrite.class.getMethod("delete", String.class);
        var annotation = method.getAnnotation(RequiresConfirmation.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.summary())
                .isEqualTo("Delete contact ${contactId}");
    }
}
