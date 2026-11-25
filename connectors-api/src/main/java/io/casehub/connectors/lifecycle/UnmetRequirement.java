package io.casehub.connectors.lifecycle;

import java.util.List;

public record UnmetRequirement(String capability, List<String> sub, String reason, List<ResolutionOption> options) {}
