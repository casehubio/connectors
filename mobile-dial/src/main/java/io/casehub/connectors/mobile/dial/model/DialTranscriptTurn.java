package io.casehub.connectors.mobile.dial.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DialTranscriptTurn(
        String speaker,
        String text,
        @JsonProperty("startMs") long startMs,
        @JsonProperty("endMs") long endMs) {
}
