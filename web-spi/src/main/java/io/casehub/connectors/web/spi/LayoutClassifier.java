package io.casehub.connectors.web.spi;

import io.casehub.connectors.web.model.LayoutClassification;

@FunctionalInterface
public interface LayoutClassifier {
    LayoutClassification classify(byte[] pageImage, String mimeType);
}
