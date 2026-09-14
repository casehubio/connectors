package io.casehub.connectors.teams;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.http.HttpHelper;

import java.util.logging.Logger;

public class TeamsConnector implements Connector {

    public static final String ID = "teams";

    private static final Logger LOG = Logger.getLogger(TeamsConnector.class.getName());

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean send(final ConnectorMessage message) {
        final String  json = buildPayload(message.title(), message.body());
        final boolean ok   = HttpHelper.postJson(message.destination(), json);
        if (!ok) {
            LOG.warning("Teams connector failed for destination: " + message.destination());
        }
        return ok;
    }

    public static String buildPayload(final String title, final String body) {
        final String safeTitle = title != null ? HttpHelper.jsonEscape(title) : "";
        final String safeBody = body != null ? HttpHelper.jsonEscape(body) : "";
        return "{"
                + "\"type\":\"message\","
                + "\"attachments\":[{"
                + "\"contentType\":\"application/vnd.microsoft.card.adaptive\","
                + "\"content\":{"
                + "\"type\":\"AdaptiveCard\","
                + "\"version\":\"1.4\","
                + "\"body\":["
                + (safeTitle.isEmpty() ? "" :
                        "{\"type\":\"TextBlock\",\"text\":\"" + safeTitle + "\","
                        + "\"weight\":\"Bolder\",\"size\":\"Medium\"},")
                + "{\"type\":\"TextBlock\",\"text\":\"" + safeBody + "\","
                + "\"wrap\":true}"
                + "]}}]}";
    }
}
