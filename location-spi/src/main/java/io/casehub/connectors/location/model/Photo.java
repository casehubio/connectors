package io.casehub.connectors.location.model;

import java.util.List;

public record Photo(String reference, int width, int height, List<String> attributions) {}
