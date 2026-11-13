package io.casehub.connectors.mobile.dial.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DialLookupResult(
        String number,
        String carrier,
        Set<String> capabilities,
        @JsonProperty("countryCode") String countryCode,
        @JsonProperty("numberType") String numberType) {
}
