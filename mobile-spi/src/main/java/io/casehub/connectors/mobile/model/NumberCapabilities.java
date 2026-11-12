package io.casehub.connectors.mobile.model;

import java.util.Set;

public record NumberCapabilities(
        String number,
        String carrier,
        Set<NumberCapability> capabilities,
        String countryCode,
        NumberType numberType) {
}
