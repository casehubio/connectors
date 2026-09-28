package io.casehub.connectors;

import java.util.List;

public class UnsupportedCapabilityException extends RuntimeException {

    private final String operation;
    private final String capability;
    private final String provider;
    private final List<String> supportedCapabilities;

    public UnsupportedCapabilityException(String operation, String capability,
                                          String provider, List<String> supportedCapabilities) {
        super(provider + " does not support " + capability);
        this.operation = operation;
        this.capability = capability;
        this.provider = provider;
        this.supportedCapabilities = List.copyOf(supportedCapabilities);
    }

    public String operation() { return operation; }
    public String capability() { return capability; }
    public String provider() { return provider; }
    public List<String> supportedCapabilities() { return supportedCapabilities; }
}
