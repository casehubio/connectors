package io.casehub.connectors.lifecycle;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public class FamilyManifestLoader {

    private static final Logger LOG = Logger.getLogger(FamilyManifestLoader.class.getName());
    private static final String FAMILIES_DIR = "META-INF/families/";
    private static final JsonMapper MAPPER = JsonMapper.builder(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .build();

    public static Map<String, FamilyManifest> discover() {
        var result = new LinkedHashMap<String, FamilyManifest>();
        try {
            var cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> dirs = cl.getResources(FAMILIES_DIR);
            while (dirs.hasMoreElements()) {
                var dirUrl = dirs.nextElement();
                if ("file".equals(dirUrl.getProtocol())) {
                    var dirPath = Path.of(dirUrl.toURI());
                    if (Files.isDirectory(dirPath)) {
                        try (var stream = Files.list(dirPath)) {
                            stream.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                                    .forEach(p -> loadFamilyFile(result, p));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to discover family manifests: " + e.getMessage());
        }
        return Map.copyOf(result);
    }

    private static void loadFamilyFile(Map<String, FamilyManifest> result, Path path) {
        try (var is = Files.newInputStream(path)) {
            var manifest = MAPPER.readValue(is, FamilyManifest.class);
            if (manifest != null && manifest.family() != null && manifest.family().id() != null) {
                result.put(manifest.family().id(), manifest);
            }
        } catch (IOException e) {
            LOG.warning("Failed to load family manifest from " + path + ": " + e.getMessage());
        }
    }
}
