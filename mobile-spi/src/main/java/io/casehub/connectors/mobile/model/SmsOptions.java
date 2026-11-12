package io.casehub.connectors.mobile.model;

import java.util.List;

public record SmsOptions(String fromNumberId, List<String> mediaUrls) {
}
