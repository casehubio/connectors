package io.casehub.connectors.graphql;

import io.casehub.connectors.SyncRequest;
import io.casehub.connectors.SyncResult;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.spi.DocumentPlatformService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

@McpDomain(value = "connectors/documents", app = "connectors",
    basePath = "/api/connectors/documents",
    summary = "Document connector — files, folders, sync")
@ApplicationScoped
public class ConnectorDocumentApi {

    @Inject DocumentPlatformService documentService;

    @PlatformQuery("Incremental sync of documents")
    @RestPath("/sync")
    public SyncResult<DocumentSummary> syncDocuments(
            @QueryParam("platform") String platformId,
            @QueryParam("syncToken") String syncToken,
            @QueryParam("pageSize") Integer pageSize) {
        var p = documentService.platform(platformId);
        int size = pageSize != null ? pageSize : 100;
        var request = syncToken != null
            ? new SyncRequest(syncToken, size)
            : SyncRequest.initial(size);
        return p.files().listSync(request);
    }
}
