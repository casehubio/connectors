package io.casehub.connectors.document.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.document.model.DocumentMetadata;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.model.Folder;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class InMemoryDocumentBackend implements DocumentBackend {

    private final List<Folder>                        folders         = new ArrayList<>();
    private final Map<String, List<DocumentMetadata>> filesByFolder   = new LinkedHashMap<>();
    private final Map<String, byte[]>                 content         = new LinkedHashMap<>();
    private final AtomicLong                          version         = new AtomicLong(0);
    private final Map<String, Long>                   fileVersions    = new LinkedHashMap<>();
    private final Map<String, Long>                   deletedVersions = new LinkedHashMap<>();

    public InMemoryDocumentBackend() {
        loadData();
    }

    private void loadData() {
        var now           = Instant.parse("2026-09-15T10:00:00Z");
        var docsFolder    = new Folder("folder-docs", "Documents", "root", now);
        var reportsFolder = new Folder("folder-reports", "Reports", "root", now);
        var archiveFolder = new Folder("folder-archive", "Archive", "root",
                                       now.minusSeconds(86400 * 30));
        folders.add(docsFolder);
        folders.add(reportsFolder);
        folders.add(archiveFolder);

        addFile("folder-docs", "doc-001", "Q3 Quarterly Plan.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                2048, "user@example.com", now);
        addFile("folder-docs", "doc-002", "Architecture Overview.pdf",
                "application/pdf", 4096, "alice@example.com",
                now.plusSeconds(3600));
        addFile("folder-docs", "doc-003", "Meeting Notes 2026-09-10.md",
                "text/markdown", 512, "user@example.com",
                now.plusSeconds(7200));
        addFile("folder-docs", "doc-004", "API Design Guidelines.md",
                "text/markdown", 1024, "bob@example.com",
                now.plusSeconds(10800));

        addFile("folder-reports", "rpt-001", "Monthly Revenue Report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                8192, "carol@example.com", now);
        addFile("folder-reports", "rpt-002", "Quarterly Performance Review.pdf",
                "application/pdf", 3072, "dave@example.com",
                now.plusSeconds(3600));

        addFile("folder-archive", "arc-001", "Old Project Spec.pdf",
                "application/pdf", 1536, "user@example.com",
                now.minusSeconds(86400 * 60));
    }

    private void addFile(String folderId, String id, String name,
                         String contentType, int size, String owner,
                         Instant createdAt) {
        var metadata = new DocumentMetadata(id, name, folderId, contentType,
                                            size, owner, "https://docs.example.com/view/" + id,
                                            createdAt, createdAt);
        filesByFolder.computeIfAbsent(folderId, k -> new ArrayList<>()).add(metadata);
        content.put(id, new byte[size]);
        fileVersions.put(id, version.incrementAndGet());
    }

    @Override
    public List<Folder> listFolders(String parentId) {
        return folders.stream()
                      .filter(f -> parentId.equals(f.parentId()))
                      .toList();
    }

    @Override
    public Folder createFolder(String parentId, String name) {
        var folder = new Folder(UUID.randomUUID().toString(), name, parentId,
                                Instant.now());
        folders.add(folder);
        return folder;
    }

    @Override
    public Page<DocumentSummary> listFiles(String folderId, PageRequest pagination) {
        var files      = filesByFolder.getOrDefault(folderId, List.of());
        int startIndex = 0;
        if (pagination.cursor() != null) {
            try {
                startIndex = Integer.parseInt(pagination.cursor());
            } catch (NumberFormatException e) {
                startIndex = 0;
            }
        }
        int endIndex = Math.min(startIndex + pagination.pageSize(), files.size());
        var pageItems = files.subList(startIndex, endIndex).stream()
                             .map(m -> new DocumentSummary(m.id(), m.name(), m.folderId(),
                                                           m.contentType(), m.size(), m.createdAt(), m.modifiedAt()))
                             .toList();
        boolean hasMore    = endIndex < files.size();
        String  nextCursor = hasMore ? String.valueOf(endIndex) : null;
        return new Page<>(pageItems, nextCursor, hasMore);
    }

    @Override
    public DocumentMetadata getFile(String fileId) {
        return filesByFolder.values().stream()
                            .flatMap(List::stream)
                            .filter(m -> m.id().equals(fileId))
                            .findFirst()
                            .orElseThrow(() -> new NoSuchElementException(
                                    "File '" + fileId + "' not found"));
    }

    @Override
    public byte[] downloadContent(String fileId) {
        getFile(fileId);
        byte[] data = content.get(fileId);
        if (data == null) {
            throw new NoSuchElementException(
                    "Content not found for file '" + fileId + "'");
        }
        return data;
    }

    @Override
    public DocumentMetadata uploadFile(String folderId, String name,
                                       String contentType, byte[] fileContent) {
        String id  = UUID.randomUUID().toString();
        var    now = Instant.now();
        var metadata = new DocumentMetadata(id, name, folderId, contentType,
                                            fileContent.length, "user@example.com",
                                            "https://docs.example.com/view/" + id, now, now);
        filesByFolder.computeIfAbsent(folderId, k -> new ArrayList<>()).add(metadata);
        content.put(id, fileContent.clone());
        fileVersions.put(id, version.incrementAndGet());
        return metadata;
    }

    @Override
    public void deleteFile(String fileId) {
        var metadata    = getFile(fileId);
        var folderFiles = filesByFolder.get(metadata.folderId());
        if (folderFiles == null || !folderFiles.removeIf(m -> m.id().equals(fileId))) {
            throw new NoSuchElementException("File '" + fileId + "' not found");
        }
        content.remove(fileId);
        deletedVersions.put(fileId, version.incrementAndGet());
        fileVersions.remove(fileId);
    }

    @Override
    public void moveFile(String fileId, String targetFolderId) {
        var metadata    = getFile(fileId);
        var sourceFiles = filesByFolder.get(metadata.folderId());
        if (sourceFiles != null) {
            sourceFiles.removeIf(m -> m.id().equals(fileId));
        }
        var moved = new DocumentMetadata(metadata.id(), metadata.name(),
                                         targetFolderId, metadata.contentType(), metadata.size(),
                                         metadata.owner(), metadata.webViewLink(),
                                         metadata.createdAt(), Instant.now());
        filesByFolder.computeIfAbsent(targetFolderId, k -> new ArrayList<>()).add(moved);
        fileVersions.put(fileId, version.incrementAndGet());
    }

    @Override
    public Page<DocumentSummary> search(String query, PageRequest pagination) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        var matches = filesByFolder.values().stream()
                                   .flatMap(List::stream)
                                   .filter(m -> m.name().toLowerCase(Locale.ROOT).contains(lowerQuery))
                                   .map(m -> new DocumentSummary(m.id(), m.name(), m.folderId(),
                                                                 m.contentType(), m.size(), m.createdAt(), m.modifiedAt()))
                                   .toList();
        int startIndex = 0;
        if (pagination.cursor() != null) {
            try {
                startIndex = Integer.parseInt(pagination.cursor());
            } catch (NumberFormatException e) {
                startIndex = 0;
            }
        }
        int     endIndex   = Math.min(startIndex + pagination.pageSize(), matches.size());
        var     pageItems  = matches.subList(startIndex, endIndex);
        boolean hasMore    = endIndex < matches.size();
        String  nextCursor = hasMore ? String.valueOf(endIndex) : null;
        return new Page<>(pageItems, nextCursor, hasMore);
    }

    @Override
    public String getShareLink(String fileId) {
        var metadata = getFile(fileId);
        return metadata.webViewLink();
    }

    @Override
    public long currentVersion() {
        return version.get();
    }

    @Override
    public List<DocumentSummary> changedSince(long sinceVersion) {
        return fileVersions.entrySet().stream()
                           .filter(e -> e.getValue() > sinceVersion)
                           .map(e -> {
                               var meta = getFile(e.getKey());
                               return new DocumentSummary(meta.id(), meta.name(), meta.folderId(),
                                                          meta.contentType(), meta.size(), meta.createdAt(), meta.modifiedAt());
                           })
                           .toList();
    }

    @Override
    public List<String> deletedSince(long sinceVersion) {
        return deletedVersions.entrySet().stream()
                              .filter(e -> e.getValue() > sinceVersion)
                              .map(Map.Entry::getKey)
                              .toList();
    }
}
