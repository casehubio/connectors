package io.casehub.connectors.web.spi;

@FunctionalInterface
public interface ImageDescriber {

    String describe(byte[] imageBytes, String mimeType);
}
