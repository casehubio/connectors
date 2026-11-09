package io.casehub.connectors.web.model;

public record ImageRegion(
    String panelId,
    int pageNumber,
    String captionText,
    String ocrText,
    String llmDescription,
    byte[] imageBytes
) {
    public ImageRegion {
        if (captionText == null) captionText = "";
        if (ocrText == null) ocrText = "";
    }
}
