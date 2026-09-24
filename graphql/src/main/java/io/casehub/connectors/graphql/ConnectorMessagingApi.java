package io.casehub.connectors.graphql;

import io.casehub.connectors.ConnectorMeshBridge;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.ConnectorService;
import io.casehub.connectors.graphql.dto.OperationResult;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpDomain(value = "connectors/messaging", app = "connectors", basePath = "/api/connectors/messaging", summary = "Connector messaging — dispatch and receive via external channels")
@ApplicationScoped
public class ConnectorMessagingApi {

    @Inject ConnectorService connectorService;
    @Inject ConnectorMeshBridge meshBridge;

    @PlatformMutation("Send an SMS message via Twilio")
    @RestPath("/sms")
    public OperationResult sendSms(String to, String body) {
        try {
            connectorService.send("twilio-sms", new ConnectorMessage(to, body));
            meshBridge.notifyDelivered("twilio-sms", to, sanitize(body));
            return new OperationResult(true, "twilio-sms", to, "Dispatched");
        } catch (Exception e) {
            return new OperationResult(false, "twilio-sms", to, e.getMessage());
        }
    }

    @PlatformMutation("Send a WhatsApp message")
    @RestPath("/whatsapp")
    public OperationResult sendWhatsApp(String to, String body,
                                    String templateName, String templateLanguage) {
        try {
            var msg = new ConnectorMessage(to, body);
            if (templateName != null) {
                msg = new ConnectorMessage(to, templateName, body,
                    java.util.Map.of("templateLanguage",
                        templateLanguage != null ? templateLanguage : "en"));
            }
            connectorService.send("whatsapp", msg);
            meshBridge.notifyDelivered("whatsapp", to, sanitize(body));
            return new OperationResult(true, "whatsapp", to, "Dispatched");
        } catch (Exception e) {
            return new OperationResult(false, "whatsapp", to, e.getMessage());
        }
    }

    @PlatformMutation("Send an email via SMTP")
    @RestPath("/email")
    public OperationResult sendEmail(String to, String subject, String body) {
        try {
            connectorService.send("email",
                new ConnectorMessage(to, subject, body, java.util.Map.of()));
            meshBridge.notifyDelivered("email", to, sanitize(body));
            return new OperationResult(true, "email", to, "Dispatched");
        } catch (Exception e) {
            return new OperationResult(false, "email", to, e.getMessage());
        }
    }

    @PlatformMutation("Send a message to a Slack webhook")
    @RestPath("/slack")
    public OperationResult sendSlack(String webhookUrl, String title, String body) {
        try {
            connectorService.send("slack-webhook",
                new ConnectorMessage(webhookUrl, title, body, java.util.Map.of()));
            meshBridge.notifyDelivered("slack-webhook", webhookUrl, sanitize(body));
            return new OperationResult(true, "slack-webhook", webhookUrl, "Dispatched");
        } catch (Exception e) {
            return new OperationResult(false, "slack-webhook", webhookUrl, e.getMessage());
        }
    }

    @PlatformMutation("Send a message to a Microsoft Teams webhook")
    @RestPath("/teams")
    public OperationResult sendTeams(String webhookUrl, String title, String body) {
        try {
            connectorService.send("teams-webhook",
                new ConnectorMessage(webhookUrl, title, body, java.util.Map.of()));
            meshBridge.notifyDelivered("teams-webhook", webhookUrl, sanitize(body));
            return new OperationResult(true, "teams-webhook", webhookUrl, "Dispatched");
        } catch (Exception e) {
            return new OperationResult(false, "teams-webhook", webhookUrl, e.getMessage());
        }
    }

    private static String sanitize(String text) {
        if (text == null) return "";
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
