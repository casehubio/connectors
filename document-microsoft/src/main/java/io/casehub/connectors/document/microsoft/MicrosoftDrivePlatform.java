package io.casehub.connectors.document.microsoft;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.document.model.DocumentMetadata;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.model.Folder;
import io.casehub.connectors.document.model.Site;
import io.casehub.connectors.document.spi.DocumentPlatform;
import io.casehub.connectors.document.spi.SiteDiscovery;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;
import io.casehub.connectors.microsoft.TokenRetry;
import io.casehub.platform.api.authn.ServiceConnectionProvider;

import java.util.List;
import java.util.Set;


public class MicrosoftDrivePlatform implements DocumentPlatform, SiteDiscovery {

    private final MicrosoftDriveClient      client;
    private final MicrosoftGraphClient      graph;
    private final Set<String>               allowedCdnHosts;
    private final ServiceConnectionProvider connectionProvider;
    private final String                    platformId;

    public MicrosoftDrivePlatform(MicrosoftDriveClient client,
                                  ServiceConnectionProvider connectionProvider, String platformId) {
        this(client, null, null, connectionProvider, platformId);
    }

    public MicrosoftDrivePlatform(MicrosoftDriveClient client,
                                  MicrosoftGraphClient graph, Set<String> allowedCdnHosts,
                                  ServiceConnectionProvider connectionProvider, String platformId) {
        this.client             = client;
        this.graph              = graph;
        this.allowedCdnHosts    = allowedCdnHosts;
        this.connectionProvider = connectionProvider;
        this.platformId         = platformId;
    }

    MicrosoftDrivePlatform(MicrosoftDriveClient client, String platformId) {
        this(client, null, null, null, platformId);
    }

    @Override
    public String id() {
        return platformId;
    }

    @Override
    public boolean supports(Class<?> capability) {
        if (client == null) {return false;}
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

    @Override
    public List<Site> searchSites(String userId, String query) {
        if (graph == null) {return List.of();}
        return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                                         token -> {
                                             String escaped = query.replace("'", "''");
                                             var    result  = graph.get("/sites?search=" + escaped, token);
                                             var    sites   = new java.util.ArrayList<Site>();
                                             for (var item : result.getJsonArray("value")) {
                                                 var obj = item.asJsonObject();
                                                 sites.add(new Site(
                                                         obj.getString("id"),
                                                         obj.getString("displayName", null),
                                                         obj.getString("description", null),
                                                         obj.getString("webUrl", null)));
                                             }
                                             return List.copyOf(sites);
                                         });
    }

    private void requireConfigured() {
        if (client == null) {
            throw new IllegalStateException(platformId + " provider not configured");
        }
    }

    private String resolveToken(String userId) {
        if (connectionProvider == null) {return "test-token";}
        return connectionProvider.getAccessToken(userId, "microsoft", "default")
                                 .accessToken();
    }

    private MicrosoftDriveClient resolveClient(String siteId) {
        if (siteId == null || graph == null) {return client;}
        var hosts = allowedCdnHosts != null ? allowedCdnHosts
                                            : MicrosoftDriveClient.DEFAULT_CDN_SUFFIXES;
        return new MicrosoftDriveClient(graph, "/sites/" + siteId + "/drive", hosts);
    }

    private class MicrosoftFileOperations implements FileOperations {
        private final String userId;

        MicrosoftFileOperations(String userId) {this.userId = userId;}

        @Override
        public Page<DocumentSummary> list(String folderId, PageRequest pagination) {
            var parsed      = SharePointId.parse(folderId);
            var driveClient = resolveClient(parsed.siteId());
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                                             token -> {
                                                 var page = driveClient.listChildren(parsed.itemId(), pagination, token);
                                                 return new Page<>(
                                                         page.items().stream()
                                                             .map(item -> DriveMapper.toSummary(item, parsed.siteId()))
                                                             .toList(),
                                                         page.nextCursor(), page.hasMore());
                                             });
        }

        @Override
        public DocumentMetadata get(String fileId) {
            var parsed      = SharePointId.parse(fileId);
            var driveClient = resolveClient(parsed.siteId());
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                                             token -> DriveMapper.toMetadata(
                                                     driveClient.getItem(parsed.itemId(), token), parsed.siteId()));
        }

        @Override
        public byte[] download(String fileId) {
            var parsed      = SharePointId.parse(fileId);
            var driveClient = resolveClient(parsed.siteId());
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                                             token -> driveClient.downloadContent(parsed.itemId(), token));
        }

        @Override
        public DocumentMetadata upload(String folderId, String name,
                                       String contentType, byte[] content) {
            var parsed      = SharePointId.parse(folderId);
            var driveClient = resolveClient(parsed.siteId());
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                                             token -> DriveMapper.toMetadata(
                                                     driveClient.upload(parsed.itemId(), name, contentType, content, token),
                                                     parsed.siteId()));
        }

        @Override
        public void delete(String fileId) {
            var parsed      = SharePointId.parse(fileId);
            var driveClient = resolveClient(parsed.siteId());
            TokenRetry.withTokenRetryVoid(() -> resolveToken(userId),
                                          token -> driveClient.deleteItem(parsed.itemId(), token));
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

        MicrosoftFolderOperations(String userId) {this.userId = userId;}

        @Override
        public List<Folder> list(String parentId) {
            var parsed      = SharePointId.parse(parentId);
            var driveClient = resolveClient(parsed.siteId());
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                                             token -> driveClient.listFolders(parsed.itemId(), token).stream()
                                                                 .map(item -> DriveMapper.toFolder(item, parsed.siteId()))
                                                                 .toList());
        }

        @Override
        public Folder create(String parentId, String name) {
            var parsed      = SharePointId.parse(parentId);
            var driveClient = resolveClient(parsed.siteId());
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                                             token -> DriveMapper.toFolder(
                                                     driveClient.createFolder(parsed.itemId(), name, token),
                                                     parsed.siteId()));
        }

        @Override
        public void move(String fileId, String targetFolderId) {
            var parsed       = SharePointId.parse(fileId);
            var targetParsed = SharePointId.parse(targetFolderId);
            var driveClient  = resolveClient(parsed.siteId());
            TokenRetry.withTokenRetryVoid(() -> resolveToken(userId),
                                          token -> driveClient.moveItem(parsed.itemId(), targetParsed.itemId(), token));
        }
    }

    private class MicrosoftSearchOperations implements SearchOperations {
        private final String userId;

        MicrosoftSearchOperations(String userId) {this.userId = userId;}

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

        MicrosoftSharingOperations(String userId) {this.userId = userId;}

        @Override
        public String getShareLink(String fileId) {
            var parsed      = SharePointId.parse(fileId);
            var driveClient = resolveClient(parsed.siteId());
            return TokenRetry.withTokenRetry(() -> resolveToken(userId),
                                             token -> driveClient.createShareLink(parsed.itemId(), token));
        }
    }
}

