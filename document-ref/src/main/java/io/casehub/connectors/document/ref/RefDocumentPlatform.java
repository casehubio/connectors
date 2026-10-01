package io.casehub.connectors.document.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.document.model.DocumentMetadata;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.model.Folder;
import io.casehub.connectors.document.spi.DocumentPlatform;

import java.util.List;

public class RefDocumentPlatform implements DocumentPlatform {

    private final DocumentBackend backend;
    private final FileOperations fileOps;
    private final FolderOperations folderOps;
    private final SearchOperations searchOps;
    private final SharingOperations sharingOps;

    public RefDocumentPlatform(DocumentBackend backend) {
        this.backend = backend;
        this.fileOps = new RefFileOperations(backend);
        this.folderOps = new RefFolderOperations(backend);
        this.searchOps = new RefSearchOperations(backend);
        this.sharingOps = new RefSharingOperations(backend);
    }

    @Override
    public String id() {
        return "ref";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return capability == FileOperations.class
            || capability == FolderOperations.class
            || capability == SearchOperations.class
            || capability == SharingOperations.class;
    }

    @Override
    public FileOperations files() {
        return fileOps;
    }

    @Override
    public FolderOperations folders() {
        return folderOps;
    }

    @Override
    public SearchOperations search() {
        return searchOps;
    }

    @Override
    public SharingOperations sharing() {
        return sharingOps;
    }

    private record RefFileOperations(DocumentBackend backend) implements FileOperations {
        @Override
        public Page<DocumentSummary> list(String folderId, PageRequest pagination) {
            return backend.listFiles(folderId, pagination);
        }

        @Override
        public DocumentMetadata get(String fileId) {
            return backend.getFile(fileId);
        }

        @Override
        public byte[] download(String fileId) {
            return backend.downloadContent(fileId);
        }

        @Override
        public DocumentMetadata upload(String folderId, String name,
                                       String contentType, byte[] content) {
            return backend.uploadFile(folderId, name, contentType, content);
        }

        @Override
        public void delete(String fileId) {
            backend.deleteFile(fileId);
        }

        @Override
        public SyncResult<DocumentSummary> listSync(SyncRequest request) {
            long sinceVersion = request.syncToken() != null
                                ? Long.parseLong(request.syncToken()) : 0;
            var changed = backend.changedSince(sinceVersion);
            var deleted = backend.deletedSince(sinceVersion);
            return new SyncResult<>(changed, deleted,
                    String.valueOf(backend.currentVersion()), false);
        }

    }

    private record RefFolderOperations(DocumentBackend backend) implements FolderOperations {
        @Override
        public List<Folder> list(String parentId) {
            return backend.listFolders(parentId);
        }

        @Override
        public Folder create(String parentId, String name) {
            return backend.createFolder(parentId, name);
        }

        @Override
        public void move(String fileId, String targetFolderId) {
            backend.moveFile(fileId, targetFolderId);
        }
    }

    private record RefSearchOperations(DocumentBackend backend) implements SearchOperations {
        @Override
        public Page<DocumentSummary> search(String query, PageRequest pagination) {
            return backend.search(query, pagination);
        }
    }

    private record RefSharingOperations(DocumentBackend backend) implements SharingOperations {
        @Override
        public String getShareLink(String fileId) {
            return backend.getShareLink(fileId);
        }
    }
}
