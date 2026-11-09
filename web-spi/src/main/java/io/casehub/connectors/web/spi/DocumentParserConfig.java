package io.casehub.connectors.web.spi;

public record DocumentParserConfig(
    boolean ocrEnabled,
    boolean llmImageDescriptionEnabled,
    boolean llmLayoutDetectionEnabled,
    int maxPages
) {
    public static DocumentParserConfig defaults() {
        return new DocumentParserConfig(true, false, false, Integer.MAX_VALUE);
    }
}
