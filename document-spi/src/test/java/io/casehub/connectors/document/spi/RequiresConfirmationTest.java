package io.casehub.connectors.document.spi;

import io.casehub.platform.api.confirmation.RequiresConfirmation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequiresConfirmationTest {

    @Test
    void fileDeleteRequiresConfirmation() throws Exception {
        var method = DocumentPlatform.FileOperations.class.getMethod("delete", String.class);
        var annotation = method.getAnnotation(RequiresConfirmation.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.summary())
                .isEqualTo("Delete file ${fileId}");
    }
}
