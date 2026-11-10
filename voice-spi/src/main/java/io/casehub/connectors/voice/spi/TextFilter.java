package io.casehub.connectors.voice.spi;

public interface TextFilter {
    String apply(String text);

    String name();

    int destructiveness();
}
