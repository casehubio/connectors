package io.casehub.connectors.mobile.dial.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DialCall(
        String id,
        String to,
        String from,
        @JsonProperty("fromNumberId") String fromNumberId,
        String status,
        @JsonProperty("outboundInstruction") String outboundInstruction,
        String language,
        String transcript,
        @JsonProperty("transcriptTurns") List<DialTranscriptTurn> transcriptTurns,
        Integer duration,
        @JsonProperty("startedAt") String startedAt,
        @JsonProperty("endedAt") String endedAt,
        @JsonProperty("failureReason") String failureReason) {
}
