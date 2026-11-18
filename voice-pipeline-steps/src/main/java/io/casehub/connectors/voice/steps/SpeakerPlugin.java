package io.casehub.connectors.voice.steps;

import io.casehub.connectors.voice.model.SpeakerMatch;
import io.casehub.connectors.voice.spi.SpeakerEmbeddingExtractor;
import io.casehub.connectors.voice.spi.SpeakerRegistry;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Plugin(value = "speaker", description = "Speaker registration and identification", absorbSiblings = true)
public record SpeakerPlugin(
        @Required String operation,
        @Optional String name,
        @Optional byte[] audio,
        @Optional Double threshold,
        @Optional List<Integer> supportedSampleRates) {

    private static final Set<Integer> DEFAULT_SAMPLE_RATES = Set.of(16000);

    @Execute
    public Result execute(SpeakerEmbeddingExtractor extractor, SpeakerRegistry registry) {
        try {
            return switch (operation) {
                case "register" -> register(extractor, registry);
                case "identify" -> identify(extractor, registry);
                default -> Result.failed("Unknown speaker operation: " + operation);
            };
        } catch (Exception e) {
            return Result.failed("speaker " + operation + ": " + e.getMessage());
        }
    }

    private Set<Integer> effectiveRates() {
        return supportedSampleRates != null
               ? new LinkedHashSet<>(supportedSampleRates)
               : DEFAULT_SAMPLE_RATES;
    }

    private Result register(SpeakerEmbeddingExtractor extractor, SpeakerRegistry registry) {
        if (name == null) {return Result.failed("speaker register: 'name' is required");}
        if (audio == null) {return Result.failed("speaker register: 'audio' is required");}

        var embedding = extractFromWav(extractor, audio, effectiveRates());
        registry.register(name, embedding);
        return Result.of(Map.of("registered", name, "dimensions", embedding.dimensions()));
    }

    private Result identify(SpeakerEmbeddingExtractor extractor, SpeakerRegistry registry) {
        if (audio == null) {return Result.failed("speaker identify: 'audio' is required");}

        var    embedding = extractFromWav(extractor, audio, effectiveRates());
        double thresh    = threshold != null ? threshold : 0.8;
        var    match     = registry.identify(embedding, thresh);

        var output = new LinkedHashMap<String, Object>();
        output.put("identified", match.isPresent());
        output.put("name", match.map(SpeakerMatch::name).orElse("unknown"));
        output.put("confidence", match.map(SpeakerMatch::confidence).orElse(0.0));
        return Result.of(output);
    }

    private static io.casehub.connectors.voice.model.SpeakerEmbedding extractFromWav(
            SpeakerEmbeddingExtractor extractor, byte[] wav, Set<Integer> allowedRates) {
        if (wav.length < 44
            || wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F'
            || wav[8] != 'W' || wav[9] != 'A' || wav[10] != 'V' || wav[11] != 'E') {
            throw new IllegalArgumentException("Not a valid WAV file");
        }

        int audioFormat   = readShortLE(wav, 20);
        int channels      = readShortLE(wav, 22);
        int sampleRate    = readIntLE(wav, 24);
        int bitsPerSample = readShortLE(wav, 34);

        if (audioFormat != 1) {
            throw new IllegalArgumentException(
                    "Unsupported WAV: expected PCM format (1), got " + audioFormat);
        }

        if (channels != 1) {
            throw new IllegalArgumentException(
                    "Unsupported WAV: expected mono (1 channel), got " + channels);
        }
        if (!allowedRates.contains(sampleRate)) {
            throw new IllegalArgumentException(
                    "Unsupported WAV sample rate: " + sampleRate + " Hz, supported: " + allowedRates + " Hz");
        }
        if (bitsPerSample != 16) {
            throw new IllegalArgumentException(
                    "Unsupported WAV: expected 16-bit samples, got " + bitsPerSample + "-bit");
        }

        int     dataOffset  = findDataChunk(wav);
        int     dataSize    = readIntLE(wav, dataOffset - 4);
        int     sampleCount = dataSize / 2;
        float[] samples     = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int idx = dataOffset + i * 2;
            if (idx + 1 < wav.length) {
                short s = (short) ((wav[idx + 1] << 8) | (wav[idx] & 0xFF));
                samples[i] = s / (float) Short.MAX_VALUE;
            }
        }
        return extractor.extract(samples, sampleRate);
    }

    private static int findDataChunk(byte[] wav) {
        int pos = 12;
        while (pos + 8 <= wav.length) {
            if (wav[pos] == 'd' && wav[pos + 1] == 'a'
                && wav[pos + 2] == 't' && wav[pos + 3] == 'a') {
                return pos + 8;
            }
            int chunkSize = readIntLE(wav, pos + 4);
            if (chunkSize <= 0) {
                throw new IllegalArgumentException("WAV file has corrupt chunk at offset " + pos);
            }
            pos += 8 + chunkSize;
        }
        throw new IllegalArgumentException("WAV file has no data chunk");
    }

    private static int readShortLE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF) | ((buf[offset + 1] & 0xFF) << 8);
    }

    private static int readIntLE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF) | ((buf[offset + 1] & 0xFF) << 8)
               | ((buf[offset + 2] & 0xFF) << 16) | ((buf[offset + 3] & 0xFF) << 24);
    }
}
