package io.casehub.connectors.location.model;

import java.util.List;

public record OpeningHours(List<String> weekdayText, boolean openNow) {}
