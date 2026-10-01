package io.casehub.connectors.document.spi;

import io.casehub.connectors.Page;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.document.model.DocumentMetadata;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.model.Folder;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpDocumentPlatform implements DocumentPlatform {

    static final String NOT_CONFIGURED = "No document provider configured";

    @Override
    public String id() {
        return "none";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return false;
    }

    @Override
    public FileOperations files() {
        return NoOpFileOperations.INSTANCE;
    }

    @Override
    public FolderOperations folders() {
        return NoOpFolderOperations.INSTANCE;
    }

    @Override
    public SearchOperations search() {
        return NoOpSearchOperations.INSTANCE;
    }

    @Override
    public SharingOperations sharing() {
        return NoOpSharingOperations.INSTANCE;
    }

    enum NoOpFileOperations implements FileOperations {
        INSTANCE;

        @Override
        public Page<DocumentSummary> list(String folderId, PageRequest pagination) {
            return Page.of(List.of());
        }

        @Override
        public DocumentMetadata get(String fileId) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }

        @Override
        public byte[] download(String fileId) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }

        @Override
        public DocumentMetadata upload(String folderId, String name,
                                       String contentType, byte[] content) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }

        @Override
        public void delete(String fileId) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }

        @Override
        public SyncResult<DocumentSummary> listSync(SyncRequest request) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }

    }

    enum NoOpFolderOperations implements FolderOperations {
        INSTANCE;

        @Override
        public List<Folder> list(String parentId) {
            return List.of();
        }

        @Override
        public Folder create(String parentId, String name) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }

        @Override
        public void move(String fileId, String targetFolderId) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }
    }

    enum NoOpSearchOperations implements SearchOperations {
        INSTANCE;

        @Override
        public Page<DocumentSummary> search(String query, PageRequest pagination) {
            return Page.of(List.of());
        }
    }

    enum NoOpSharingOperations implements SharingOperations {
        INSTANCE;

        @Override
        public String getShareLink(String fileId) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }
    }
}
