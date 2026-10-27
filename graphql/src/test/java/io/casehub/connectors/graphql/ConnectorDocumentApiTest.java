package io.casehub.connectors.graphql;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.document.model.DocumentMetadata;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.model.Folder;
import io.casehub.connectors.document.spi.DocumentPlatform;
import io.casehub.connectors.document.spi.DocumentPlatformService;
import io.casehub.connectors.graphql.dto.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectorDocumentApiTest {

    private ConnectorDocumentApi api;
    private DocumentPlatform.FileOperations fileOps;
    private DocumentPlatform.FolderOperations folderOps;
    private DocumentPlatform.SearchOperations searchOps;
    private DocumentPlatform.SharingOperations sharingOps;

    @BeforeEach
    void setUp() {
        var platform = mock(DocumentPlatform.class);
        fileOps = mock(DocumentPlatform.FileOperations.class);
        folderOps = mock(DocumentPlatform.FolderOperations.class);
        searchOps = mock(DocumentPlatform.SearchOperations.class);
        sharingOps = mock(DocumentPlatform.SharingOperations.class);

        when(platform.id()).thenReturn("test");
        when(platform.files()).thenReturn(fileOps);
        when(platform.folders()).thenReturn(folderOps);
        when(platform.search()).thenReturn(searchOps);
        when(platform.sharing()).thenReturn(sharingOps);
        when(platform.supports(DocumentPlatform.FileOperations.class)).thenReturn(true);
        when(platform.supports(DocumentPlatform.FolderOperations.class)).thenReturn(true);
        when(platform.supports(DocumentPlatform.SearchOperations.class)).thenReturn(true);
        when(platform.supports(DocumentPlatform.SharingOperations.class)).thenReturn(true);

        api = new ConnectorDocumentApi();
        api.documentService = new DocumentPlatformService(List.of(platform));
    }

    @Test
    void listFilesDelegatesToPlatform() {
        var doc = testDoc("f1");
        when(fileOps.list(eq("folder-1"), any(PageRequest.class)))
            .thenReturn(new Page<>(List.of(doc), null, false));

        var result = api.listFiles("test", "folder-1", null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().id()).isEqualTo("f1");
    }

    @Test
    void getFileDelegatesToPlatform() {
        var meta = testMetadata("f1");
        when(fileOps.get("f1")).thenReturn(meta);

        var result = api.getFile("test", "f1");

        assertThat(result.id()).isEqualTo("f1");
        assertThat(result.name()).isEqualTo("f1.txt");
    }

    @Test
    void downloadFileDelegatesToPlatform() {
        byte[] content = "hello".getBytes();
        when(fileOps.download("f1")).thenReturn(content);

        var result = api.downloadFile("test", "f1");

        assertThat(result).isEqualTo(content);
    }

    @Test
    void uploadFileDelegatesToPlatform() {
        var meta = testMetadata("f-new");
        byte[] content = "data".getBytes();
        when(fileOps.upload("folder-1", "report.pdf", "application/pdf", content))
            .thenReturn(meta);

        var result = api.uploadFile("test", "folder-1", "report.pdf", "application/pdf", content);

        assertThat(result.id()).isEqualTo("f-new");
    }

    @Test
    void deleteFileReturnsSuccess() {
        var result = api.deleteFile("test", "f1");

        assertThat(result.dispatched()).isTrue();
        verify(fileOps).delete("f1");
    }

    @Test
    void deleteFileReturnsFailureOnException() {
        org.mockito.Mockito.doThrow(new RuntimeException("Not found")).when(fileOps).delete("f-bad");

        var result = api.deleteFile("test", "f-bad");

        assertThat(result.dispatched()).isFalse();
        assertThat(result.detail()).isEqualTo("Not found");
    }

    @Test
    void syncDocumentsDelegatesToPlatform() {
        when(fileOps.listSync(any()))
            .thenReturn(new SyncResult<>(List.of(), List.of("d1"), "token-2", false));

        var result = api.syncDocuments("test", "token-1", 50);

        assertThat(result.deletedIds()).containsExactly("d1");
        assertThat(result.syncToken()).isEqualTo("token-2");
    }

    @Test
    void listFoldersDelegatesToPlatform() {
        var folder = new Folder("fold-1", "Documents", null, Instant.now());
        when(folderOps.list(null)).thenReturn(List.of(folder));

        var result = api.listFolders("test", null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Documents");
    }

    @Test
    void createFolderDelegatesToPlatform() {
        var folder = new Folder("fold-new", "Reports", "fold-1", Instant.now());
        when(folderOps.create("fold-1", "Reports")).thenReturn(folder);

        var result = api.createFolder("test", "fold-1", "Reports");

        assertThat(result.id()).isEqualTo("fold-new");
        assertThat(result.name()).isEqualTo("Reports");
    }

    @Test
    void moveFileReturnsSuccess() {
        var result = api.moveFile("test", "f1", "fold-2");

        assertThat(result.dispatched()).isTrue();
        verify(folderOps).move("f1", "fold-2");
    }

    @Test
    void searchDocumentsDelegatesToPlatform() {
        var doc = testDoc("f-found");
        when(searchOps.search(eq("report"), any(PageRequest.class)))
            .thenReturn(new Page<>(List.of(doc), null, false));

        var result = api.searchDocuments("test", "report", null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().id()).isEqualTo("f-found");
    }

    @Test
    void getShareLinkDelegatesToPlatform() {
        when(sharingOps.getShareLink("f1")).thenReturn("https://share.example.com/f1");

        var result = api.getShareLink("test", "f1");

        assertThat(result).isEqualTo("https://share.example.com/f1");
    }

    @Test
    void requireCapabilityThrowsWhenUnsupported() {
        var platform = mock(DocumentPlatform.class);
        when(platform.id()).thenReturn("limited");
        when(platform.files()).thenReturn(fileOps);
        when(platform.supports(DocumentPlatform.FileOperations.class)).thenReturn(true);
        when(platform.supports(DocumentPlatform.FolderOperations.class)).thenReturn(false);
        when(platform.supports(DocumentPlatform.SearchOperations.class)).thenReturn(false);
        when(platform.supports(DocumentPlatform.SharingOperations.class)).thenReturn(false);

        var limitedApi = new ConnectorDocumentApi();
        limitedApi.documentService = new DocumentPlatformService(List.of(platform));

        assertThatThrownBy(() -> limitedApi.listFolders("limited", null))
            .isInstanceOf(UnsupportedCapabilityException.class)
            .hasMessageContaining("limited")
            .hasMessageContaining("FolderOperations");
    }

    private DocumentSummary testDoc(String id) {
        return new DocumentSummary(id, id + ".txt", "folder-1",
            "text/plain", 1024, Instant.now(), Instant.now());
    }

    private DocumentMetadata testMetadata(String id) {
        return new DocumentMetadata(id, id + ".txt", "folder-1",
            "text/plain", 1024, "owner", null, Instant.now(), Instant.now());
    }
}
