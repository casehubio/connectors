package io.casehub.connectors.notification;

import io.casehub.connectors.ConnectorMessage;
import io.casehub.platform.api.delivery.DigestSummary;
import io.casehub.platform.api.notification.NotificationInput;
import java.util.Comparator;

public class SmsDigestFormatter implements DigestFormatter {

    private static final Comparator<NotificationInput> BY_SEVERITY =
            Comparator.comparing(NotificationInput::severity);

    @Override
    public String channelId() {
        return "sms";
    }

    @Override
    public ConnectorMessage format(DigestSummary summary, String destination) {
        var mostUrgent = summary.notifications().stream()
                                .max(BY_SEVERITY)
                                .orElseThrow();
        String body = summary.notifications().size() + " notifications. Most urgent: "
                      + mostUrgent.title();
        return new ConnectorMessage(destination, body);
    }
}
