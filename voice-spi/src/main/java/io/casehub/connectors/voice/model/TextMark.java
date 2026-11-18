package io.casehub.connectors.voice.model;

import java.util.Objects;

public record TextMark(String id, int charOffset) {
    public TextMark {
        Objects.requireNonNull(id, "id");
        if (charOffset < 0) {
            throw new IllegalArgumentException("charOffset (" + charOffset + ") < 0");
        }
    }
}
