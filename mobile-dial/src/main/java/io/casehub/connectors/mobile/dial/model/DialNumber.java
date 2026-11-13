package io.casehub.connectors.mobile.dial.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DialNumber(
        String id,
        String number,
        Set<String> capabilities,
        String status) {
}
