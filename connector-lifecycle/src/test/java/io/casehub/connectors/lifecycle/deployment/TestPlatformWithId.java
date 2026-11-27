package io.casehub.connectors.lifecycle.deployment;

public class TestPlatformWithId {

    private final String apiKey;
    private final String platformId;

    public TestPlatformWithId(String apiKey, String platformId) {
        this.apiKey = apiKey;
        this.platformId = platformId;
    }

    public TestPlatformWithId() {
        this("", "");
    }

    public String apiKey() { return apiKey; }

    public String platformId() { return platformId; }
}
