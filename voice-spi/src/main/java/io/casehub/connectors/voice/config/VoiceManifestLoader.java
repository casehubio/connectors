package io.casehub.connectors.voice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.connectors.voice.model.VoiceDescriptor;
import org.jboss.logging.Logger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VoiceManifestLoader {

    private static final Logger LOG = Logger.getLogger(VoiceManifestLoader.class);
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                                                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                                .enable(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

    public VoiceManifest load(Path projectDir, String profile) {
        var entries = new ArrayList<PrioritizedManifest>();
        addClasspathResource(entries, "voices/voice-config.yaml", 0);
        addFileIfExists(entries, Path.of("/etc/casehub/voice-config.yaml"), 10);
        var home = Path.of(System.getProperty("user.home"));
        addFileIfExists(entries, home.resolve(".casehub/voice-config.yaml"), 20);
        if (profile != null) {
            addFileIfExists(entries, home.resolve(".casehub/voice-config-" + profile + ".yaml"), 25);
        }
        if (projectDir != null) {
            addFileIfExists(entries, projectDir.resolve("voice-config.yaml"), 30);
            if (profile != null) {
                addFileIfExists(entries, projectDir.resolve("voice-config-" + profile + ".yaml"), 35);
            }
        }
        return merge(entries);
    }

    public VoiceManifest loadResource(String classpath) {
        try (var is = getClass().getClassLoader().getResourceAsStream(classpath)) {
            if (is == null) return emptyManifest();
            return injectIds(mapper.readValue(is, VoiceManifest.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public VoiceManifest loadFile(Path path) {
        try (var is = Files.newInputStream(path)) {
            var bytes = is.readAllBytes();
            if (bytes.length == 0) {return emptyManifest();}
            var manifest = mapper.readValue(bytes, VoiceManifest.class);
            return manifest != null ? injectIds(manifest) : emptyManifest();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    VoiceManifest merge(List<PrioritizedManifest> entries) {
        var sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(PrioritizedManifest::priority));
        var                   providers = new LinkedHashMap<String, VoiceProviderDeclaration>();
        var                   voices    = new LinkedHashMap<String, VoiceDescriptor>();
        var                   aliases   = new LinkedHashMap<String, VoiceAliasDeclaration>();
        VoiceManifestDefaults defaults  = null;
        for (var entry : sorted) {
            var m = entry.manifest;
            for (var p : m.providers()) {providers.put(p.vendor(), p);}
            voices.putAll(m.voices());
            aliases.putAll(m.aliases());
            if (m.defaults() != null) {defaults = m.defaults();}
        }
        return new VoiceManifest(new ArrayList<>(providers.values()), voices, aliases, defaults);
    }

    private VoiceManifest injectIds(VoiceManifest manifest) {
        if (manifest.voices().isEmpty()) return manifest;
        var withIds = new LinkedHashMap<String, VoiceDescriptor>();
        for (var e : manifest.voices().entrySet()) {
            var v = e.getValue();
            withIds.put(e.getKey(), new VoiceDescriptor(
                e.getKey(), v.vendor(), v.voiceId(), v.name(), v.gender(), v.accent(),
                v.quality(), v.languages(), v.style(), v.sampleRate(), v.bitDepth(),
                v.channels(), v.capability(), v.properties()));
        }
        return new VoiceManifest(manifest.providers(), withIds, manifest.aliases(), manifest.defaults());
    }

    private void addClasspathResource(List<PrioritizedManifest> entries, String resource, int priority) {
        try (var is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is != null) {
                var manifest = injectIds(mapper.readValue(is, VoiceManifest.class));
                entries.add(new PrioritizedManifest(manifest, priority));
                LOG.debugf("Loaded voice config from classpath: %s (priority %d)", resource, priority);
            }
        } catch (IOException e) {
            LOG.warnf("Failed to load voice config from classpath %s: %s", resource, e.getMessage());
        }
    }

    private void addFileIfExists(List<PrioritizedManifest> entries, Path path, int priority) {
        if (Files.isRegularFile(path)) {
            entries.add(new PrioritizedManifest(loadFile(path), priority));
            LOG.debugf("Loaded voice config from file: %s (priority %d)", path, priority);
        }
    }

    private static VoiceManifest emptyManifest() {
        return new VoiceManifest(List.of(), Map.of(), Map.of(), null);
    }

    record PrioritizedManifest(VoiceManifest manifest, int priority) {}
}
