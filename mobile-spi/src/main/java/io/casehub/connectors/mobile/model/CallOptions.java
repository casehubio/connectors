package io.casehub.connectors.mobile.model;

public record CallOptions(String fromNumberId, String language, Integer maxDurationSeconds, String transferTo) {

    public static CallOptions defaults() {
        return new CallOptions(null, null, null, null);
    }
}
