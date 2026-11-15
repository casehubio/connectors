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
    public FileOperations files(String userId) {
        requireConfigured();
        return new MicrosoftFileOperations(userId);
    }

    @Override
    public FolderOperations folders(String userId) {
        requireConfigured();
        return new MicrosoftFolderOperations(userId);
    }

    @Override
    public SearchOperations search(String userId) {
        requireConfigured();
        return new MicrosoftSearchOperations(userId);
    }

    @Override
    public SharingOperations sharing(String userId) {
        requireConfigured();
        return new MicrosoftSharingOperations(userId);
    }

    private void requireConfigured() {
        if (client == null) {
            throw new IllegalStateException(platformId + " provider not configured");
        }
    }

    private String resolveToken(String userId) {
        if (connectionProvider == null) return "test-token";
        return connectionProvider.getAccessToken(userId, "microsoft", "default")
                .accessToken();
    }

    private class MicrosoftFileOperations implements FileOperations {
        private final String userId;
        MicrosoftFileOperations(String userId) { this.userId = userId; }

        @Override
        public Page<DocumentSummary> list(String folderId, PageRequest pagination) {
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                    token -> {
                        var page = client.listChildren(folderId, pagination, token);
                        return new Page<>(
                                page.items().stream().map(DriveMapper::toSummary).toList(),
                                page.nextCursor(), page.hasMore());
                    });
        }

        @Override
        public DocumentMetadata get(String fileId) {
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                    token -> DriveMapper.toMetadata(client.getItem(fileId, token)));
        }

        @Override
        public byte[] download(String fileId) {
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                    token -> client.downloadContent(fileId, token));
        }

        @Override
        public DocumentMetadata upload(String folderId, String name,
                String contentType, byte[] content) {
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                    token -> DriveMapper.toMetadata(
                            client.upload(folderId, name, contentType, content, token)));
        }

        @Override
        public void delete(String fileId) {
            TokenRetry.withTokenRetryVoid(() -> resolveToken(userId),
                    token -> client.deleteItem(fileId, token));
        }

        @Override
        public SyncResult<DocumentSummary> listSync(SyncRequest request) {
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
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
        private final String userId;
        MicrosoftFolderOperations(String userId) { this.userId = userId; }

        @Override
        public List<Folder> list(String parentId) {
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                    token -> client.listFolders(parentId, token).stream()
                            .map(DriveMapper::toFolder).toList());
        }

        @Override
        public Folder create(String parentId, String name) {
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                    token -> DriveMapper.toFolder(
                            client.createFolder(parentId, name, token)));
        }

        @Override
        public void move(String fileId, String targetFolderId) {
            TokenRetry.withTokenRetryVoid(() -> resolveToken(userId),
                    token -> client.moveItem(fileId, targetFolderId, token));
        }
    }

    private class MicrosoftSearchOperations implements SearchOperations {
        private final String userId;
        MicrosoftSearchOperations(String userId) { this.userId = userId; }

        @Override
        public Page<DocumentSummary> search(String query, PageRequest pagination) {
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                    token -> {
                        var page = client.search(query, pagination, token);
                        return new Page<>(
                                page.items().stream().map(DriveMapper::toSummary).toList(),
                                page.nextCursor(), page.hasMore());
                    });
        }
    }

    private class MicrosoftSharingOperations implements SharingOperations {
        private final String userId;
        MicrosoftSharingOperations(String userId) { this.userId = userId; }

        @Override
        public String getShareLink(String fileId) {
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                    token -> client.createShareLink(fileId, token));
        }
    }
}
