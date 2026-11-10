package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.spi.StreamingTextToSpeech;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Qwen3VoicePool implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger("casehub-speech");

    private final Qwen3VoicePoolConfig config;
    private final Map<String, Path> voices;
    private final LinkedHashMap<String, Slot> active;
    private final ReentrantLock lock = new ReentrantLock();
    private final Path binary;
    private final Path modelDir;

    record Slot(Process process, int port, Qwen3TextToSpeech tts) {}

    private Qwen3VoicePool(Qwen3VoicePoolConfig config, Map<String, Path> voices,
                           Path binary, Path modelDir) {
        this.config = config;
        this.voices = Map.copyOf(voices);
        this.active = new LinkedHashMap<>(16, 0.75f, true);
        this.binary = binary;
        this.modelDir = modelDir;
    }

    public static Qwen3VoicePool create(Qwen3VoicePoolConfig config) {
        return new Qwen3VoicePool(config, discoverVoices(config), null, null);
    }

    public static Qwen3VoicePool createProvisioned(Qwen3VoicePoolConfig config) {
        Path binary = Provisioner.ensureQwen3Binary();
        Path modelDir = Provisioner.ensureQwen3Model(config.modelVariant());
        return new Qwen3VoicePool(config, discoverVoices(config), binary, modelDir);
    }

    private static Map<String, Path> discoverVoices(Qwen3VoicePoolConfig config) {
        Map<String, Path> discovered = new LinkedHashMap<>();
        if (Files.isDirectory(config.voiceDir())) {
            try (DirectoryStream<Path> stream =
                         Files.newDirectoryStream(config.voiceDir(), "*.qvoice")) {
                for (Path path : stream) {
                    String name = path.getFileName().toString();
                    name = name.substring(0, name.length() - ".qvoice".length());
                    discovered.put(name, path);
                }
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Failed to scan voice directory: {0}", e.getMessage());
            }
        }
        return discovered;
    }

    public List<String> availableVoices() {
        var sorted = new ArrayList<>(voices.keySet());
        Collections.sort(sorted);
        return Collections.unmodifiableList(sorted);
    }

    public StreamingTextToSpeech forVoice(String name) {
        if (!voices.containsKey(name)) {
            throw new IllegalArgumentException("Unknown voice: " + name
                    + ". Available: " + availableVoices());
        }
        return new Qwen3ClonedVoice(this, name);
    }

    Slot acquireSlot(String voiceName) {
        lock.lock();
        try {
            Slot existing = active.get(voiceName);
            if (existing != null && existing.process().isAlive()) {
                return existing;
            }
            if (existing != null) {
                active.remove(voiceName);
            }

            if (active.size() >= config.maxActive()) {
                evictLru();
            }

            int port = allocatePort(voiceName);
            Slot slot = startVoiceServer(voiceName, port);
            active.put(voiceName, slot);
            return slot;
        } finally {
            lock.unlock();
        }
    }

    private void evictLru() {
        var it = active.entrySet().iterator();
        if (it.hasNext()) {
            var entry = it.next();
            LOG.log(System.Logger.Level.INFO, "Evicting LRU voice server: {0}", entry.getKey());
            killSlot(entry.getValue());
            it.remove();
        }
    }

    int allocatePort(String voiceName) {
        var sorted = new ArrayList<>(voices.keySet());
        Collections.sort(sorted);
        return config.basePort() + sorted.indexOf(voiceName);
    }

    private Slot startVoiceServer(String voiceName, int port) {
        Path voicePath = voices.get(voiceName);
        String baseUrl = "http://localhost:" + port;
        try {
            var cmd = List.of(
                    binary.toString(),
                    "-d", modelDir.toString(),
                    "--load-voice", voicePath.toString(),
                    "--serve", String.valueOf(port),
                    "--eos-strategy", "v2",
                    "-l", "en"
            );
            var process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();

            Thread.ofVirtual().name("qwen3-pool-" + voiceName).start(() -> {
                try (var reader = process.inputReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOG.log(System.Logger.Level.DEBUG,
                                "[qwen3-pool:{0}] {1}", voiceName, line);
                    }
                } catch (IOException ignored) {}
            });

            for (int i = 0; i < 60; i++) {
                Thread.sleep(500);
                try {
                    var health = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/v1/health"))
                            .timeout(Duration.ofSeconds(2))
                            .GET().build();
                    var resp = HttpClient.newHttpClient().send(health,
                            HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        LOG.log(System.Logger.Level.INFO,
                                "Voice pool: {0} ready on port {1}", voiceName, port);
                        var tts = Qwen3TextToSpeech.withExternalServer(baseUrl, null);
                        return new Slot(process, port, tts);
                    }
                } catch (IOException ignored) {}
            }
            process.destroyForcibly();
            throw new SherpaException(
                    "Voice server for " + voiceName + " failed to start within 30s");
        } catch (SherpaException e) {
            throw e;
        } catch (Exception e) {
            throw new SherpaException("Failed to start voice server for " + voiceName
                    + ": " + e.getMessage(), e);
        }
    }

    private void killSlot(Slot slot) {
        if (slot.process().isAlive()) {
            slot.process().destroy();
            try {
                slot.process().waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                slot.process().destroyForcibly();
            }
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            for (Slot slot : active.values()) {
                killSlot(slot);
            }
            active.clear();
        } finally {
            lock.unlock();
        }
    }
}
