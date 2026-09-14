package io.casehub.connectors.notification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class NotificationBridgeBeans {

    @Produces
    @ApplicationScoped
    public EmailDigestFormatter emailDigestFormatter() {
        return new EmailDigestFormatter();
    }

    @Produces
    @ApplicationScoped
    public SmsDigestFormatter smsDigestFormatter() {
        return new SmsDigestFormatter();
    }

    @Produces
    @ApplicationScoped
    public WhatsAppDigestFormatter whatsAppDigestFormatter() {
        return new WhatsAppDigestFormatter();
    }
}
