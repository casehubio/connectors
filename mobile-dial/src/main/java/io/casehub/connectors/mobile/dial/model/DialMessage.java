package io.casehub.connectors.mobile.dial.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DialMessage(
        String id,
        String to,
        String from,
        String body,
        String channel,
        String status,
        String direction,
        String timestamp,
        @JsonProperty("fromNumberId") String fromNumberId,
        List<DialMediaAttachment> media) {
}
