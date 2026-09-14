package io.casehub.connectors.whatsapp;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.http.HttpHelper;

import java.util.logging.Logger;

public class WhatsAppConnector implements Connector {

    public static final String ID = "whatsapp";

    private static final Logger LOG = Logger.getLogger(WhatsAppConnector.class.getName());

    private final String apiToken;
    private final String phoneNumberId;

    public WhatsAppConnector(final String apiToken, final String phoneNumberId) {
        this.apiToken = apiToken;
        this.phoneNumberId = phoneNumberId;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean send(final ConnectorMessage message) {
        if (apiToken.isBlank() || phoneNumberId.isBlank()) {
            LOG.warning("WhatsAppConnector: casehub.connectors.whatsapp.* not configured — message not sent");
            return false;
        }

        final String url = "https://graph.facebook.com/v18.0/" + phoneNumberId + "/messages";
        final String to  = message.destination().replaceAll("[^0-9+]", "");
        final String templateName = message.attributes() != null
                                    ? message.attributes().get("templateName") : null;
        final String templateLanguage = (message.attributes() != null
                                         && message.attributes().get("templateLanguage") != null)
                                        ? message.attributes().get("templateLanguage") : "en_US";

        final String  json = buildPayload(to, message.body(), templateName, templateLanguage);
        final boolean ok   = HttpHelper.postJson(url, json, "Authorization", "Bearer " + apiToken);
        if (!ok) {
            LOG.warning("WhatsApp connector failed to: " + to);
        }
        return ok;
    }

    static String buildPayload(final String to, final String body,
                               final String templateName, final String templateLanguage) {
        if (templateName != null && !templateName.isBlank()) {
            return "{"
                    + "\"messaging_product\":\"whatsapp\","
                    + "\"to\":" + HttpHelper.jsonQuote(to) + ","
                    + "\"type\":\"template\","
                    + "\"template\":{"
                    + "\"name\":" + HttpHelper.jsonQuote(templateName) + ","
                    + "\"language\":{\"code\":" + HttpHelper.jsonQuote(templateLanguage) + "}"
                    + "}"
                    + "}";
        }
        final String text = body != null ? body : "";
        return "{"
                + "\"messaging_product\":\"whatsapp\","
                + "\"to\":" + HttpHelper.jsonQuote(to) + ","
                + "\"type\":\"text\","
                + "\"text\":{\"body\":" + HttpHelper.jsonQuote(text) + "}"
                + "}";
    }
}
