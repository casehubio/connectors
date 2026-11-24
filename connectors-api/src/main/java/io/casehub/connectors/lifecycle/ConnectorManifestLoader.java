package io.casehub.connectors.lifecycle;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

public class ConnectorManifestLoader {

    private static final Logger LOG = Logger.getLogger(ConnectorManifestLoader.class.getName());
    private static final String RESOURCE_PATH = "META-INF/connector-manifest.yaml";
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static List<ConnectorManifest> discoverFromClasspath() {
        var result = new ArrayList<ConnectorManifest>();
        try {
            var resources = Thread.currentThread().getContextClassLoader().getResources(RESOURCE_PATH);
            while (resources.hasMoreElements()) {
                var url = resources.nextElement();
                try (var is = url.openStream()) {
                    var manifest = MAPPER.readValue(is, ConnectorManifest.class);
                    if (manifest != null && manifest.connector() != null) result.add(manifest);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to discover connector manifests from classpath", e);
        }
        return List.copyOf(result);
    }

    public static List<ConnectorManifest> discover() {
        return discover(null, null);
    }

    public static List<ConnectorManifest> discover(Path projectDir, String profile) {
        var all = new ArrayList<>(discoverFromClasspath());
        addFileIfExists(all, Path.of("/etc/casehub/connector-manifest.yaml"));
        var home = Path.of(System.getProperty("user.home"));
        addFileIfExists(all, home.resolve(".casehub/connector-manifest.yaml"));
        if (profile != null) addFileIfExists(all, home.resolve(".casehub/connector-manifest-" + profile + ".yaml"));
        if (projectDir != null) {
            addFileIfExists(all, projectDir.resolve("connector-manifest.yaml"));
            if (profile != null) addFileIfExists(all, projectDir.resolve("connector-manifest-" + profile + ".yaml"));
        }
        return merge(all);
    }

    private static void addFileIfExists(List<ConnectorManifest> list, Path path) {
        if (Files.isReadable(path)) {
            try (var is = Files.newInputStream(path)) {
                var manifest = MAPPER.readValue(is, ConnectorManifest.class);
                if (manifest != null) list.add(manifest);
            } catch (IOException e) {
                LOG.warning("Failed to load connector manifest from " + path + ": " + e.getMessage());
            }
        }
    }

    static List<ConnectorManifest> merge(List<ConnectorManifest> manifests) {
        var byId = new LinkedHashMap<String, ConnectorManifest>();
        for (var m : manifests) {
            byId.put(m.connector().id(), m);
        }
        return List.copyOf(byId.values());
    }
}
