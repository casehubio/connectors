package io.casehub.connectors.mobile.model;

import java.util.List;

public record MessagingOptions(MessagingChannel channel, String fromNumberId, List<String> mediaUrls, String templateId) {
}
