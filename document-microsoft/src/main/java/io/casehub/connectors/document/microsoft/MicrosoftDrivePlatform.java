package io.casehub.connectors.document.microsoft;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.document.model.DocumentMetadata;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.model.Folder;
import io.casehub.connectors.document.spi.DocumentPlatform;
import io.casehub.connectors.microsoft.TokenRetry;
import io.casehub.platform.api.authn.ServiceConnectionProvider;

import java.util.List;

public class MicrosoftDrivePlatform implements DocumentPlatform {

    private final MicrosoftDriveClient client;
    private final ServiceConnectionProvider connectionProvider;
    private final String platformId;

    public MicrosoftDrivePlatform(MicrosoftDriveClient client,
            ServiceConnectionProvider connectionProvider, String platformId) {
        this.client = client;
        this.connectionProvider = connectionProvider;
        this.platformId = platformId;
    }

    MicrosoftDrivePlatform(MicrosoftDriveClient client, String platformId) {
        this(client, null, platformId);
    }

    @Override
    public String id() {
        return platformId;
    }

    @Override
    public boolean supports(Class<?> capability) {
        if (client == null) return false;
        return capability == FileOperations.class
                || capability == FolderOperations.class
                || capability == SearchOperations.class
                || capability == SharingOperations.class;
    }

    @Override
    public FileOperations files() {
        requireConfigured();
        return new MicrosoftFileOperations();
    }

    @Override
    public FolderOperations folders() {
        requireConfigured();
        return new MicrosoftFolderOperations();
    }

    @Override
    public SearchOperations search() {
        requireConfigured();
        return new MicrosoftSearchOperations();
    }

    @Override
    public SharingOperations sharing() {
        requireConfigured();
        return new MicrosoftSharingOperations();
    }

    private void requireConfigured() {
        if (client == null) {
            throw new IllegalStateException(platformId + " provider not configured");
        }
    }

    private String resolveToken() {
        if (connectionProvider == null) return "test-token";
        return connectionProvider.getAccessToken(null, "microsoft", "default")
                .accessToken();
    }

    private class MicrosoftFileOperations implements FileOperations {

        @Override
        public Page<DocumentSummary> list(String folderId, PageRequest pagination) {
            return TokenRetry.withTokenRetry(MicrosoftDrivePlatform.this::resolveToken,
                    token -> {
                        var page = client.listChildren(folderId, pagination, token);
                        return new Page<>(
                                page.items().stream().map(DriveMapper::toSummary).toList(),
                                page.nextCursor(), page.hasMore());
                    });
        }

        @Override
        public DocumentMetadata get(String fileId) {
            return TokenRetry.withTokenRetry(MicrosoftDrivePlatform.this::resolveToken,
                    token -> DriveMapper.toMetadata(client.getItem(fileId, token)));
        }

        @Override
        public byte[] download(String fileId) {
            return TokenRetry.withTokenRetry(MicrosoftDrivePlatform.this::resolveToken,
                    token -> client.downloadContent(fileId, token));
        }

        @Override
        public DocumentMetadata upload(String folderId, String name,
                String contentType, byte[] content) {
            return TokenRetry.withTokenRetry(MicrosoftDrivePlatform.this::resolveToken,
                    token -> DriveMapper.toMetadata(
                            client.upload(folderId, name, contentType, content, token)));
        }

        @Override
        public void delete(String fileId) {
            TokenRetry.withTokenRetryVoid(MicrosoftDrivePlatform.this::resolveToken,
                    token -> client.deleteItem(fileId, token));
        }

        @Override
        public SyncResult<DocumentSummary> listSync(SyncRequest request) {
            return TokenRetry.withTokenRetry(MicrosoftDrivePlatform.this::resolveToken,
                    token -> {
                        var delta = client.deltaSync(request.syncToken(), token);
                        return new SyncResult<>(
                                delta.changed().stream()
                                        .map(DriveMapper::toSummary).toList(),
                                delta.deletedIds(),
                                delta.deltaLink(),
                                false);
                    });
        }
    }

    private class MicrosoftFolderOperations implements FolderOperations {

        @Override
        public List<Folder> list(String parentId) {
            return TokenRetry.withTokenRetry(MicrosoftDrivePlatform.this::resolveToken,
                    token -> client.listFolders(parentId, token).stream()
                            .map(DriveMapper::toFolder).toList());
        }

        @Override
        public Folder create(String parentId, String name) {
            return TokenRetry.withTokenRetry(MicrosoftDrivePlatform.this::resolveToken,
                    token -> DriveMapper.toFolder(
                            client.createFolder(parentId, name, token)));
        }

        @Override
        public void move(String fileId, String targetFolderId) {
            TokenRetry.withTokenRetryVoid(MicrosoftDrivePlatform.this::resolveToken,
                    token -> client.moveItem(fileId, targetFolderId, token));
        }
    }

    private class MicrosoftSearchOperations implements SearchOperations {

        @Override
        public Page<DocumentSummary> search(String query, PageRequest pagination) {
            return TokenRetry.withTokenRetry(MicrosoftDrivePlatform.this::resolveToken,
                    token -> {
                        var page = client.search(query, pagination, token);
                        return new Page<>(
                                page.items().stream().map(DriveMapper::toSummary).toList(),
                                page.nextCursor(), page.hasMore());
                    });
        }
    }

    private class MicrosoftSharingOperations implements SharingOperations {

        @Override
        public String getShareLink(String fileId) {
            return TokenRetry.withTokenRetry(MicrosoftDrivePlatform.this::resolveToken,
                    token -> client.createShareLink(fileId, token));
        }
    }
}
