package io.casehub.connectors.document.spi;

import io.casehub.connectors.document.model.Site;

import java.util.List;

public interface SiteDiscovery {

    List<Site> searchSites(String userId, String query);
}
