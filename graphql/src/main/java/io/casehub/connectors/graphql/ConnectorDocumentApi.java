package io.casehub.connectors.graphql;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.document.model.DocumentMetadata;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.model.Folder;
import io.casehub.connectors.document.spi.DocumentPlatform;
import io.casehub.connectors.document.spi.DocumentPlatformService;
import io.casehub.connectors.graphql.dto.OperationResult;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.util.ArrayList;
import java.util.List;

@McpDomain(value = "connectors/documents", app = "connectors",
    basePath = "/api/connectors/documents",
    summary = "Document connector — files, folders, sync")
@ApplicationScoped
public class ConnectorDocumentApi {

    @Inject DocumentPlatformService documentService;

    @PlatformQuery("List files in a folder")
    @RestPath("/files")
    public Page<DocumentSummary> listFiles(
            @QueryParam("platform") String platformId,
            @QueryParam("folderId") String folderId,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        var p = documentService.platform(platformId);
        requireCapability(p, DocumentPlatform.FileOperations.class, "listFiles");
        int size = pageSize != null ? pageSize : 20;
        return p.files().list(folderId, new PageRequest(cursor, size));
    }

    @PlatformQuery("Get file metadata by ID")
    @RestPath("/files/{fileId}")
    public DocumentMetadata getFile(
            @QueryParam("platform") String platformId,
            @PathParam String fileId) {
        var p = documentService.platform(platformId);
        requireCapability(p, DocumentPlatform.FileOperations.class, "getFile");
        return p.files().get(fileId);
    }

    @PlatformQuery("Download file content")
    @RestPath("/files/{fileId}/download")
    public byte[] downloadFile(
            @QueryParam("platform") String platformId,
            @PathParam String fileId) {
        var p = documentService.platform(platformId);
        requireCapability(p, DocumentPlatform.FileOperations.class, "downloadFile");
        return p.files().download(fileId);
    }

    @PlatformMutation("Upload a file to a folder")
    @RestPath("/files")
    public DocumentMetadata uploadFile(
            @QueryParam("platform") String platformId,
            @QueryParam("folderId") String folderId,
            @QueryParam("name") String name,
            @QueryParam("contentType") String contentType,
            byte[] content) {
        var p = documentService.platform(platformId);
        requireCapability(p, DocumentPlatform.FileOperations.class, "uploadFile");
        return p.files().upload(folderId, name, contentType, content);
    }

    @PlatformMutation("Delete a file")
    @RestPath("/files/{fileId}/delete")
    public OperationResult deleteFile(
            @QueryParam("platform") String platformId,
            @PathParam String fileId) {
        var p = documentService.platform(platformId);
        requireCapability(p, DocumentPlatform.FileOperations.class, "deleteFile");
        try {
            p.files().delete(fileId);
            return new OperationResult(true, platformId, fileId, "Deleted " + fileId);
        } catch (Exception e) {
            return new OperationResult(false, platformId, fileId, e.getMessage());
        }
    }

    @PlatformQuery("Incremental sync of documents")
    @RestPath("/sync")
    public SyncResult<DocumentSummary> syncDocuments(
            @QueryParam("platform") String platformId,
            @QueryParam("syncToken") String syncToken,
            @QueryParam("pageSize") Integer pageSize) {
        var p = documentService.platform(platformId);
        requireCapability(p, DocumentPlatform.FileOperations.class, "syncDocuments");
        int size = pageSize != null ? pageSize : 100;
        var request = syncToken != null
            ? new SyncRequest(syncToken, size)
            : SyncRequest.initial(size);
        return p.files().listSync(request);
    }

    @PlatformQuery("List folders under a parent")
    @RestPath("/folders")
    public List<Folder> listFolders(
            @QueryParam("platform") String platformId,
            @QueryParam("parentId") String parentId) {
        var p = documentService.platform(platformId);
        requireCapability(p, DocumentPlatform.FolderOperations.class, "listFolders");
        return p.folders().list(parentId);
    }

    @PlatformMutation("Create a folder")
    @RestPath("/folders")
    public Folder createFolder(
            @QueryParam("platform") String platformId,
            @QueryParam("parentId") String parentId,
            @QueryParam("name") String name) {
        var p = documentService.platform(platformId);
        requireCapability(p, DocumentPlatform.FolderOperations.class, "createFolder");
        return p.folders().create(parentId, name);
    }

    @PlatformMutation("Move a file to a different folder")
    @RestPath("/files/{fileId}/move")
    public OperationResult moveFile(
            @QueryParam("platform") String platformId,
            @PathParam String fileId,
            @QueryParam("targetFolderId") String targetFolderId) {
        var p = documentService.platform(platformId);
        requireCapability(p, DocumentPlatform.FolderOperations.class, "moveFile");
        try {
            p.folders().move(fileId, targetFolderId);
            return new OperationResult(true, platformId, fileId, "Moved to " + targetFolderId);
        } catch (Exception e) {
            return new OperationResult(false, platformId, fileId, e.getMessage());
        }
    }

    @PlatformQuery("Search documents by query")
    @RestPath("/search")
    public Page<DocumentSummary> searchDocuments(
            @QueryParam("platform") String platformId,
            @QueryParam("query") String query,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        var p = documentService.platform(platformId);
        requireCapability(p, DocumentPlatform.SearchOperations.class, "searchDocuments");
        int size = pageSize != null ? pageSize : 20;
        return p.search().search(query, new PageRequest(cursor, size));
    }

    @PlatformQuery("Get a share link for a file")
    @RestPath("/files/{fileId}/share")
    public String getShareLink(
            @QueryParam("platform") String platformId,
            @PathParam String fileId) {
        var p = documentService.platform(platformId);
        requireCapability(p, DocumentPlatform.SharingOperations.class, "getShareLink");
        return p.sharing().getShareLink(fileId);
    }

    private static void requireCapability(DocumentPlatform platform, Class<?> capability, String operation) {
        if (!platform.supports(capability)) {
            var supported = new ArrayList<String>();
            if (platform.supports(DocumentPlatform.FileOperations.class)) {supported.add("FileOperations");}
            if (platform.supports(DocumentPlatform.FolderOperations.class)) {supported.add("FolderOperations");}
            if (platform.supports(DocumentPlatform.SearchOperations.class)) {supported.add("SearchOperations");}
            if (platform.supports(DocumentPlatform.SharingOperations.class)) {supported.add("SharingOperations");}
            throw new UnsupportedCapabilityException(
                    operation, capability.getSimpleName(), platform.id(), supported);
        }
    }
}
