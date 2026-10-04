package io.casehub.connectors.document.spi;

import io.casehub.connectors.Page;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.document.model.DocumentMetadata;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.model.Folder;
import io.casehub.platform.simulation.SimulationEligible;

import java.util.List;

@SimulationEligible(name = "document-platform",
    capabilities = {"fileOperations", "folderOperations", "searchOperations", "sharingOperations"})
public interface DocumentPlatform {

    String id();

    boolean supports(Class<?> capability);

    FileOperations files();

    FolderOperations folders();

    SearchOperations search();

    SharingOperations sharing();

    interface FileOperations {

        Page<DocumentSummary> list(String folderId, PageRequest pagination);

        DocumentMetadata get(String fileId);

        byte[] download(String fileId);

        DocumentMetadata upload(String folderId, String name,
                                String contentType, byte[] content);

        void delete(String fileId);

        SyncResult<DocumentSummary> listSync(SyncRequest request);

    }

    interface FolderOperations {

        List<Folder> list(String parentId);

        Folder create(String parentId, String name);

        void move(String fileId, String targetFolderId);
    }

    interface SearchOperations {

        Page<DocumentSummary> search(String query, PageRequest pagination);
    }

    interface SharingOperations {

        String getShareLink(String fileId);
    }
}
