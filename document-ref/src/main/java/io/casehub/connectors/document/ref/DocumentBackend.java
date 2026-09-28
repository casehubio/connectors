package io.casehub.connectors.document.ref;

import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.document.model.DocumentMetadata;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.model.Folder;

public interface DocumentBackend {

    Page<DocumentSummary> listFiles(String folderId, PageRequest pagination);

    DocumentMetadata getFile(String fileId);

    byte[] downloadContent(String fileId);

    DocumentMetadata uploadFile(String folderId, String name,
                                String contentType, byte[] content);

    void deleteFile(String fileId);

    List<Folder> listFolders(String parentId);

    Folder createFolder(String parentId, String name);

    void moveFile(String fileId, String targetFolderId);

    Page<DocumentSummary> search(String query, PageRequest pagination);

    String getShareLink(String fileId);
}
