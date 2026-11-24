package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.ConnectorManifest;
import io.quarkus.builder.item.SimpleBuildItem;

import java.util.List;

public final class ManifestBuildItem extends SimpleBuildItem {

    private final List<ConnectorManifest> manifests;

    public ManifestBuildItem(List<ConnectorManifest> manifests) {
        this.manifests = List.copyOf(manifests);
    }

    public List<ConnectorManifest> getManifests() {
        return manifests;
    }
}
