package io.casehub.connectors.mobile.dial.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DialMediaAttachment(
        String url,
        @JsonProperty("contentType") String contentType,
        @JsonProperty("sizeBytes") long sizeBytes) {
}
