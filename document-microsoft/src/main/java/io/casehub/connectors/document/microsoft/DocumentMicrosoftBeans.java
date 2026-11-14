package io.casehub.connectors.document.microsoft;

import io.casehub.connectors.document.spi.DocumentPlatform;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;
import io.casehub.platform.api.authn.ServiceConnectionProvider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class DocumentMicrosoftBeans {

    private static final Logger LOG = Logger.getLogger(DocumentMicrosoftBeans.class.getName());

    @Produces
    @ApplicationScoped
    @Named("microsoft-onedrive")
    DocumentPlatform oneDrive(MicrosoftGraphClient graph,
                              ServiceConnectionProvider connectionProvider,
                              @ConfigProperty(name = "casehub.connectors.microsoft.allowed-cdn-hosts",
                                              defaultValue = ".sharepoint.com,.1drv.com,.blob.core.windows.net")
                              List<String> allowedCdnHosts) {
        return new MicrosoftDrivePlatform(
                new MicrosoftDriveClient(graph, "/me/drive", Set.copyOf(allowedCdnHosts)),
                connectionProvider, "microsoft-onedrive");
    }

    @Produces
    @ApplicationScoped
    @Named("microsoft-sharepoint")
    DocumentPlatform sharePoint(MicrosoftGraphClient graph,
                                ServiceConnectionProvider connectionProvider,
                                @ConfigProperty(name = "casehub.connectors.sharepoint.site-id",
                                                defaultValue = "") String siteId,
                                @ConfigProperty(name = "casehub.connectors.microsoft.allowed-cdn-hosts",
                                                defaultValue = ".sharepoint.com,.1drv.com,.blob.core.windows.net")
                                List<String> allowedCdnHosts) {
        if (siteId.isBlank()) {
            LOG.warning("SharePoint site-id not configured — microsoft-sharepoint provider degraded");
            return new MicrosoftDrivePlatform(null, connectionProvider, "microsoft-sharepoint");
        }
        return new MicrosoftDrivePlatform(
                new MicrosoftDriveClient(graph, "/sites/" + siteId + "/drive", Set.copyOf(allowedCdnHosts)),
                connectionProvider, "microsoft-sharepoint");
    }
}
