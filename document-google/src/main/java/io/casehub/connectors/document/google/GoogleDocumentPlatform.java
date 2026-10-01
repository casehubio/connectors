package io.casehub.connectors.document.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import io.casehub.connectors.Page;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.SyncTokenExpiredException;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.document.model.DocumentMetadata;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.model.Folder;
import io.casehub.connectors.document.spi.DocumentPlatform;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

public class GoogleDocumentPlatform implements DocumentPlatform {

    private static final Logger LOG = Logger.getLogger(GoogleDocumentPlatform.class);
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final String FILE_FIELDS = "id,name,mimeType,size,parents,owners,webViewLink,createdTime,modifiedTime";
    private static final int    MAX_PAGES   = 20;


    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private Drive driveService;

    private final FileOperations fileOps = new GoogleFileOperations();
    private final FolderOperations folderOps = new GoogleFolderOperations();
    private final SearchOperations searchOps = new GoogleSearchOperations();
    private final SharingOperations sharingOps = new GoogleSharingOperations();

    public GoogleDocumentPlatform(String clientId, String clientSecret, String refreshToken) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        init();
    }

    GoogleDocumentPlatform(Drive driveService) {
        this.clientId = "";
        this.clientSecret = "";
        this.refreshToken = "";
        this.driveService = driveService;
    }

    void init() {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            LOG.warn("Google Drive credentials not configured — platform inactive");
            return;
        }
        try {
            var credentials = UserCredentials.newBuilder()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setRefreshToken(refreshToken)
                    .build();
            driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("casehub-connectors")
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            LOG.errorf(e, "Failed to initialize Google Drive client");
        }
    }

    @Override
    public String id() {
        return "google";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return capability == FileOperations.class
            || capability == FolderOperations.class
            || capability == SearchOperations.class
            || capability == SharingOperations.class;
    }

    @Override public FileOperations files() { return fileOps; }
    @Override public FolderOperations folders() { return folderOps; }
    @Override public SearchOperations search() { return searchOps; }
    @Override public SharingOperations sharing() { return sharingOps; }

    boolean isActive() {
        return driveService != null;
    }

    private void requireClient() {
        if (driveService == null) {
            throw new IllegalStateException(
                    "Google Drive client not initialised — check credentials configuration");
        }
    }

    static DocumentMetadata toMetadata(File file) {
        String folderId = file.getParents() != null && !file.getParents().isEmpty()
                ? file.getParents().getFirst() : null;
        String owner = file.getOwners() != null && !file.getOwners().isEmpty()
                ? file.getOwners().getFirst().getEmailAddress() : null;
        long size = file.getSize() != null ? file.getSize() : 0;
        Instant created = file.getCreatedTime() != null
                ? Instant.ofEpochMilli(file.getCreatedTime().getValue()) : null;
        Instant modified = file.getModifiedTime() != null
                ? Instant.ofEpochMilli(file.getModifiedTime().getValue()) : null;

        return new DocumentMetadata(file.getId(), file.getName(), folderId,
                file.getMimeType(), size, owner, file.getWebViewLink(),
                created, modified);
    }

    static DocumentSummary toSummary(File file) {
        String folderId = file.getParents() != null && !file.getParents().isEmpty()
                ? file.getParents().getFirst() : null;
        long size = file.getSize() != null ? file.getSize() : 0;
        Instant created = file.getCreatedTime() != null
                ? Instant.ofEpochMilli(file.getCreatedTime().getValue()) : null;
        Instant modified = file.getModifiedTime() != null
                ? Instant.ofEpochMilli(file.getModifiedTime().getValue()) : null;

        return new DocumentSummary(file.getId(), file.getName(), folderId,
                file.getMimeType(), size, created, modified);
    }

    private class GoogleFileOperations implements FileOperations {

        @Override
        public Page<DocumentSummary> list(String folderId, PageRequest pagination) {
            requireClient();
            try {
                var request = driveService.files().list()
                        .setQ("'" + folderId + "' in parents and mimeType != '" + FOLDER_MIME + "' and trashed = false")
                        .setFields("nextPageToken,files(" + FILE_FIELDS + ")")
                        .setPageSize(pagination.pageSize());
                if (pagination.cursor() != null) {
                    request.setPageToken(pagination.cursor());
                }
                var response = request.execute();
                var files = response.getFiles();
                if (files == null) {
                    return Page.of(List.of());
                }
                var summaries = files.stream().map(GoogleDocumentPlatform::toSummary).toList();
                String nextToken = response.getNextPageToken();
                return new Page<>(summaries, nextToken, nextToken != null);
            } catch (IOException e) {
                throw new RuntimeException("Failed to list files", e);
            }
        }

        @Override
        public DocumentMetadata get(String fileId) {
            requireClient();
            try {
                var file = driveService.files().get(fileId)
                        .setFields(FILE_FIELDS)
                        .execute();
                return toMetadata(file);
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 404) {
                    throw new NoSuchElementException("File '" + fileId + "' not found");
                }
                throw new RuntimeException("Failed to get file " + fileId, e);
            } catch (IOException e) {
                throw new RuntimeException("Failed to get file " + fileId, e);
            }
        }

        @Override
        public byte[] download(String fileId) {
            requireClient();
            try {
                var out = new ByteArrayOutputStream();
                driveService.files().get(fileId).executeMediaAndDownloadTo(out);
                return out.toByteArray();
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 404) {
                    throw new NoSuchElementException("File '" + fileId + "' not found");
                }
                throw new RuntimeException("Failed to download file " + fileId, e);
            } catch (IOException e) {
                throw new RuntimeException("Failed to download file " + fileId, e);
            }
        }

        @Override
        public DocumentMetadata upload(String folderId, String name,
                                       String contentType, byte[] content) {
            requireClient();
            try {
                var fileMetadata = new File()
                        .setName(name)
                        .setParents(List.of(folderId));
                var mediaContent = new ByteArrayContent(contentType, content);
                var request = driveService.files().create(fileMetadata, mediaContent)
                        .setFields(FILE_FIELDS);
                request.getMediaHttpUploader().setDirectUploadEnabled(true);
                var created = request.execute();
                return toMetadata(created);
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload file", e);
            }
        }

        @Override
        public void delete(String fileId) {
            requireClient();
            try {
                driveService.files().delete(fileId).execute();
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 404) {
                    throw new NoSuchElementException("File '" + fileId + "' not found");
                }
                throw new RuntimeException("Failed to delete file " + fileId, e);
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete file " + fileId, e);
            }
        }

        @Override
        public SyncResult<DocumentSummary> listSync(SyncRequest request) {
            requireClient();
            if (request.syncToken() == null) {
                return initialSync(request);
            }
            return incrementalSync(request);
        }

        private SyncResult<DocumentSummary> initialSync(SyncRequest request) {
            try {
                var    startToken = driveService.changes().getStartPageToken().execute();
                String token      = startToken.getStartPageToken();

                List<DocumentSummary> items     = new ArrayList<>();
                String                pageToken = null;
                int                   page      = 0;
                while (page < MAX_PAGES) {
                    var req = driveService.files().list()
                                          .setQ("trashed = false and mimeType != '" + FOLDER_MIME + "'")
                                          .setFields("nextPageToken,files(" + FILE_FIELDS + ")")
                                          .setPageSize(request.pageSize() > 0 ? request.pageSize() : 100);
                    if (pageToken != null) {
                        req.setPageToken(pageToken);
                    }
                    var response = req.execute();
                    if (response.getFiles() != null) {
                        response.getFiles().stream()
                                .map(GoogleDocumentPlatform::toSummary)
                                .forEach(items::add);
                    }
                    pageToken = response.getNextPageToken();
                    if (pageToken == null) {break;}
                    page++;
                }
                if (page >= MAX_PAGES) {
                    LOG.warnf("listSync initial hit MAX_PAGES (%d) — %d items accumulated", MAX_PAGES, items.size());
                }
                return new SyncResult<>(Collections.unmodifiableList(items), List.of(), token, false);
            } catch (IOException e) {
                throw new RuntimeException("Google Drive initial sync failed", e);
            }
        }

        private SyncResult<DocumentSummary> incrementalSync(SyncRequest request) {
            List<DocumentSummary> items      = new ArrayList<>();
            List<String>          deletedIds = new ArrayList<>();
            String                newToken   = null;
            try {
                String pageToken = request.syncToken();
                int    page      = 0;
                while (page < MAX_PAGES) {
                    var req = driveService.changes().list(pageToken)
                                          .setFields("nextPageToken,newStartPageToken,changes(fileId,removed,file(" + FILE_FIELDS + "))")
                                          .setPageSize(request.pageSize() > 0 ? request.pageSize() : 100);
                    var response = req.execute();
                    if (response.getChanges() != null) {
                        for (var change : response.getChanges()) {
                            if (Boolean.TRUE.equals(change.getRemoved()) || change.getFile() == null) {
                                deletedIds.add(change.getFileId());
                            } else if (!FOLDER_MIME.equals(change.getFile().getMimeType())) {
                                items.add(toSummary(change.getFile()));
                            }
                        }
                    }
                    if (response.getNewStartPageToken() != null) {
                        newToken = response.getNewStartPageToken();
                    }
                    pageToken = response.getNextPageToken();
                    if (pageToken == null) {break;}
                    page++;
                }
                if (page >= MAX_PAGES) {
                    LOG.warnf("listSync incremental hit MAX_PAGES (%d) — %d items, %d deletes accumulated",
                              MAX_PAGES, items.size(), deletedIds.size());
                }
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 404) {
                    throw new SyncTokenExpiredException(request.syncToken());
                }
                if (!items.isEmpty()) {
                    LOG.warnf(e, "listSync failed mid-pagination — returning %d partial items", items.size());
                    return new SyncResult<>(Collections.unmodifiableList(items),
                                            Collections.unmodifiableList(deletedIds), newToken, false);
                }
                throw new RuntimeException("Google Drive sync failed", e);
            } catch (IOException e) {
                if (!items.isEmpty()) {
                    LOG.warnf(e, "listSync failed mid-pagination — returning %d partial items", items.size());
                    return new SyncResult<>(Collections.unmodifiableList(items),
                                            Collections.unmodifiableList(deletedIds), newToken, false);
                }
                throw new RuntimeException("Google Drive sync failed", e);
            }
            return new SyncResult<>(Collections.unmodifiableList(items),
                                    Collections.unmodifiableList(deletedIds), newToken, false);
        }


    }

    private class GoogleFolderOperations implements FolderOperations {

        @Override
        public List<Folder> list(String parentId) {
            requireClient();
            try {
                var response = driveService.files().list()
                        .setQ("'" + parentId + "' in parents and mimeType = '" + FOLDER_MIME + "' and trashed = false")
                        .setFields("files(id,name,parents,createdTime)")
                        .execute();
                var files = response.getFiles();
                if (files == null) {
                    return List.of();
                }
                return files.stream()
                        .map(f -> new Folder(f.getId(), f.getName(), parentId,
                                f.getCreatedTime() != null
                                        ? Instant.ofEpochMilli(f.getCreatedTime().getValue())
                                        : null))
                        .toList();
            } catch (IOException e) {
                throw new RuntimeException("Failed to list folders", e);
            }
        }

        @Override
        public Folder create(String parentId, String name) {
            requireClient();
            try {
                var fileMetadata = new File()
                        .setName(name)
                        .setMimeType(FOLDER_MIME)
                        .setParents(List.of(parentId));
                var created = driveService.files().create(fileMetadata)
                        .setFields("id,name,parents,createdTime")
                        .execute();
                return new Folder(created.getId(), created.getName(), parentId,
                        created.getCreatedTime() != null
                                ? Instant.ofEpochMilli(created.getCreatedTime().getValue())
                                : Instant.now());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create folder", e);
            }
        }

        @Override
        public void move(String fileId, String targetFolderId) {
            requireClient();
            try {
                var file = driveService.files().get(fileId)
                        .setFields("parents")
                        .execute();
                String previousParents = String.join(",", file.getParents());
                driveService.files().update(fileId, null)
                        .setAddParents(targetFolderId)
                        .setRemoveParents(previousParents)
                        .execute();
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 404) {
                    throw new NoSuchElementException("File '" + fileId + "' not found");
                }
                throw new RuntimeException("Failed to move file " + fileId, e);
            } catch (IOException e) {
                throw new RuntimeException("Failed to move file " + fileId, e);
            }
        }
    }

    private class GoogleSearchOperations implements SearchOperations {

        @Override
        public Page<DocumentSummary> search(String query, PageRequest pagination) {
            requireClient();
            try {
                var request = driveService.files().list()
                        .setQ("fullText contains '" + query.replace("'", "\\'") + "' and trashed = false")
                        .setFields("nextPageToken,files(" + FILE_FIELDS + ")")
                        .setPageSize(pagination.pageSize());
                if (pagination.cursor() != null) {
                    request.setPageToken(pagination.cursor());
                }
                var response = request.execute();
                var files = response.getFiles();
                if (files == null) {
                    return Page.of(List.of());
                }
                var summaries = files.stream().map(GoogleDocumentPlatform::toSummary).toList();
                String nextToken = response.getNextPageToken();
                return new Page<>(summaries, nextToken, nextToken != null);
            } catch (IOException e) {
                throw new RuntimeException("Failed to search files", e);
            }
        }
    }

    private class GoogleSharingOperations implements SharingOperations {

        @Override
        public String getShareLink(String fileId) {
            requireClient();
            try {
                var permission = new Permission()
                        .setType("anyone")
                        .setRole("reader");
                driveService.permissions().create(fileId, permission).execute();
                var file = driveService.files().get(fileId)
                        .setFields("webViewLink")
                        .execute();
                return file.getWebViewLink();
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 404) {
                    throw new NoSuchElementException("File '" + fileId + "' not found");
                }
                throw new RuntimeException("Failed to create share link for " + fileId, e);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create share link for " + fileId, e);
            }
        }
    }
}
