package io.casehub.connectors.document.microsoft;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class DriveMapperTest {

    @Test
    void toSummaryMapsFields() {
        var json = driveItemJson("file-1", "report.pdf", "parent-1",
                "application/pdf", 1024);
        var summary = DriveMapper.toSummary(json);

        assertThat(summary.id()).isEqualTo("file-1");
        assertThat(summary.name()).isEqualTo("report.pdf");
        assertThat(summary.folderId()).isEqualTo("parent-1");
        assertThat(summary.contentType()).isEqualTo("application/pdf");
        assertThat(summary.size()).isEqualTo(1024);
        assertThat(summary.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(summary.modifiedAt()).isEqualTo(Instant.parse("2026-06-15T12:00:00Z"));
    }

    @Test
    void toMetadataMapsOwnerAndWebUrl() {
        var json = driveItemJson("file-2", "doc.docx", "parent-2",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                2048);
        var meta = DriveMapper.toMetadata(json);

        assertThat(meta.id()).isEqualTo("file-2");
        assertThat(meta.name()).isEqualTo("doc.docx");
        assertThat(meta.owner()).isEqualTo("Alice");
        assertThat(meta.webViewLink()).isEqualTo("https://onedrive.live.com/view/doc.docx");
        assertThat(meta.size()).isEqualTo(2048);
    }

    @Test
    void toMetadataHandlesMissingOwner() {
        var json = Json.createObjectBuilder()
                .add("id", "file-3")
                .add("name", "orphan.txt")
                .add("size", 10)
                .add("file", Json.createObjectBuilder().add("mimeType", "text/plain"))
                .add("createdDateTime", "2026-01-01T00:00:00Z")
                .add("lastModifiedDateTime", "2026-01-01T00:00:00Z")
                .build();
        var meta = DriveMapper.toMetadata(json);

        assertThat(meta.owner()).isNull();
        assertThat(meta.folderId()).isNull();
    }

    @Test
    void toFolderMapsFields() {
        var json = Json.createObjectBuilder()
                .add("id", "folder-1")
                .add("name", "Documents")
                .add("folder", Json.createObjectBuilder().add("childCount", 5))
                .add("parentReference", Json.createObjectBuilder().add("id", "root"))
                .add("createdDateTime", "2026-03-10T09:00:00Z")
                .build();
        var folder = DriveMapper.toFolder(json);

        assertThat(folder.id()).isEqualTo("folder-1");
        assertThat(folder.name()).isEqualTo("Documents");
        assertThat(folder.parentId()).isEqualTo("root");
        assertThat(folder.createdAt()).isEqualTo(Instant.parse("2026-03-10T09:00:00Z"));
    }

    private static JsonObject driveItemJson(String id, String name, String parentId,
            String mimeType, long size) {
        return Json.createObjectBuilder()
                .add("id", id)
                .add("name", name)
                .add("size", size)
                .add("file", Json.createObjectBuilder().add("mimeType", mimeType))
                .add("parentReference", Json.createObjectBuilder().add("id", parentId))
                .add("createdBy", Json.createObjectBuilder()
                        .add("user", Json.createObjectBuilder()
                                .add("displayName", "Alice")))
                .add("webUrl", "https://onedrive.live.com/view/" + name)
                .add("createdDateTime", "2026-01-01T00:00:00Z")
                .add("lastModifiedDateTime", "2026-06-15T12:00:00Z")
                .build();
    }
}
