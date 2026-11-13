package io.casehub.connectors.mobile.twilio;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;

import java.util.logging.Logger;

public class TwilioSmsConnectorBridge implements Connector {

    private static final Logger LOG = Logger.getLogger(TwilioSmsConnectorBridge.class.getName());
    private final TwilioMobilePlatform platform;

    TwilioSmsConnectorBridge(TwilioMobilePlatform platform) {
        this.platform = platform;
    }

    @Override
    public String id() { return "twilio-sms"; }

    @Override
    public boolean send(ConnectorMessage message) {
        try {
            var result = platform.sms().send(message.destination(), message.body() != null ? message.body() : "");
            return result.ok();
        } catch (Exception e) {
            LOG.warning("TwilioSmsConnectorBridge: send failed — " + e.getMessage());
            return false;
        }
    }

    @Override
    public String channelType() { return "sms"; }
}
