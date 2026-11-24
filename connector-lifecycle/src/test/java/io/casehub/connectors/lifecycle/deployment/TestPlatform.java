package io.casehub.connectors.lifecycle.deployment;

public class TestPlatform {

    private final String apiKey;

    public TestPlatform(String apiKey) {
        this.apiKey = apiKey;
    }

    public TestPlatform() {
        this("");
    }

    public String apiKey() {
        return apiKey;
    }
}
