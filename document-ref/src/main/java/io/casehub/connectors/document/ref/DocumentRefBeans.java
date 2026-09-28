package io.casehub.connectors.document.ref;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class DocumentRefBeans {

    @Produces
    @ApplicationScoped
    public RefDocumentPlatform refDocumentPlatform(DocumentBackend backend) {
        return new RefDocumentPlatform(backend);
    }
}
