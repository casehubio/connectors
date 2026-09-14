package io.casehub.connectors.slack;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.http.HttpHelper;

import java.util.logging.Logger;

public class SlackConnector implements Connector {

    public static final String ID = "slack";

    private static final Logger LOG = Logger.getLogger(SlackConnector.class.getName());

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean send(final ConnectorMessage message) {
        final String  json = buildPayload(message.title(), message.body());
        final boolean ok   = HttpHelper.postJson(message.destination(), json);
        if (!ok) {
            LOG.warning("Slack connector failed for destination: " + message.destination());
        }
        return ok;
    }

    public static String buildPayload(final String title, final String body) {
        final StringBuilder sb = new StringBuilder("{");
        if (title != null && !title.isBlank()) {
            sb.append("\"text\":").append(HttpHelper.jsonQuote("*" + title + "*\n" + (body != null ? body : "")));
        } else {
            sb.append("\"text\":").append(HttpHelper.jsonQuote(body));
        }
        sb.append("}");
        return sb.toString();
    }
}
