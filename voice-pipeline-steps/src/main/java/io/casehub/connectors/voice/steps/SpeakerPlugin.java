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
import java.util.Map;

@Plugin(value = "speaker", description = "Speaker registration and identification", absorbSiblings = true)
public record SpeakerPlugin(
        @Required String operation,
        @Optional String name,
        @Optional byte[] audio,
        @Optional Double threshold) {

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

    private Result register(SpeakerEmbeddingExtractor extractor, SpeakerRegistry registry) {
        if (name == null) {return Result.failed("speaker register: 'name' is required");}
        if (audio == null) {return Result.failed("speaker register: 'audio' is required");}

        var embedding = extractFromWav(extractor, audio);
        registry.register(name, embedding);
        return Result.of(Map.of("registered", name, "dimensions", embedding.dimensions()));
    }

    private Result identify(SpeakerEmbeddingExtractor extractor, SpeakerRegistry registry) {
        if (audio == null) {return Result.failed("speaker identify: 'audio' is required");}

        var    embedding = extractFromWav(extractor, audio);
        double thresh    = threshold != null ? threshold : 0.8;
        var    match     = registry.identify(embedding, thresh);

        var output = new LinkedHashMap<String, Object>();
        output.put("identified", match.isPresent());
        output.put("name", match.map(SpeakerMatch::name).orElse("unknown"));
        output.put("confidence", match.map(SpeakerMatch::confidence).orElse(0.0));
        return Result.of(output);
    }

    private static io.casehub.connectors.voice.model.SpeakerEmbedding extractFromWav(
            SpeakerEmbeddingExtractor extractor, byte[] wav) {
        int     offset      = (wav.length > 44 && wav[0] == 'R') ? 44 : 0;
        int     sampleCount = (wav.length - offset) / 2;
        float[] samples     = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int idx = offset + i * 2;
            if (idx + 1 < wav.length) {
                short s = (short) ((wav[idx + 1] << 8) | (wav[idx] & 0xFF));
                samples[i] = s / (float) Short.MAX_VALUE;
            }
        }
        return extractor.extract(samples, 16000);
    }
}
