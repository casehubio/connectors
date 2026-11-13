package io.casehub.connectors.mobile.twilio;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;

import java.util.logging.Logger;

public class TwilioWhatsAppConnectorBridge implements Connector {

    private static final Logger LOG = Logger.getLogger(TwilioWhatsAppConnectorBridge.class.getName());
    private final TwilioMobilePlatform platform;

    TwilioWhatsAppConnectorBridge(TwilioMobilePlatform platform) {
        this.platform = platform;
    }

    @Override
    public String id() { return "whatsapp"; }

    @Override
    public boolean send(ConnectorMessage message) {
        try {
            var result = platform.messaging().send(message.destination(), message.body() != null ? message.body() : "");
            return result.ok();
        } catch (Exception e) {
            LOG.warning("TwilioWhatsAppConnectorBridge: send failed — " + e.getMessage());
            return false;
        }
    }

    @Override
    public String channelType() { return "whatsapp"; }
}
