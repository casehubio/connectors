package io.casehub.connectors.web.model;

import java.util.List;

public record ImageProcessingResult(List<CapturedImage> images) {

    public static final ImageProcessingResult EMPTY = new ImageProcessingResult(List.of());

    public ImageProcessingResult {
        images = images == null ? List.of() : List.copyOf(images);
    }
}
