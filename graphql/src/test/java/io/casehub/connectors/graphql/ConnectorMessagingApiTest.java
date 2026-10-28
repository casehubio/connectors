package io.casehub.connectors.graphql;

import io.casehub.connectors.ConnectorMeshBridge;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.ConnectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectorMessagingApiTest {

    ConnectorService connectorService;
    ConnectorMeshBridge meshBridge;
    ConnectorMessagingApi api;

    @BeforeEach
    void setUp() {
        connectorService = mock(ConnectorService.class);
        meshBridge = mock(ConnectorMeshBridge.class);
        doNothing().when(meshBridge).notifyDelivered(any(), any(), any());

        api = new ConnectorMessagingApi();
        api.connectorService = connectorService;
        api.meshBridge = meshBridge;
    }

    @Test
    void sendSms_dispatchesToTwilioSms() {
        when(connectorService.send(eq("twilio-sms"), any(ConnectorMessage.class))).thenReturn(true);

        var result = api.sendSms("+447700900000", "Hello");

        assertThat(result.dispatched()).isTrue();
        assertThat(result.connector()).isEqualTo("twilio-sms");
        assertThat(result.destination()).isEqualTo("+447700900000");
    }

    @Test
    void sendSms_failureReturnsNotDispatched() {
        when(connectorService.send(eq("twilio-sms"), any(ConnectorMessage.class)))
                .thenThrow(new RuntimeException("SMS gateway down"));

        var result = api.sendSms("+447700900000", "Hello");

        assertThat(result.dispatched()).isFalse();
        assertThat(result.connector()).isEqualTo("twilio-sms");
        assertThat(result.detail()).isEqualTo("SMS gateway down");
    }

    @Test
    void sendWhatsApp_dispatchesToWhatsapp() {
        when(connectorService.send(eq("whatsapp"), any(ConnectorMessage.class))).thenReturn(true);

        var result = api.sendWhatsApp("+447700900000", "Hello", null, null);

        assertThat(result.dispatched()).isTrue();
        assertThat(result.connector()).isEqualTo("whatsapp");
    }

    @Test
    void sendWhatsApp_withTemplate_dispatchesToWhatsapp() {
        when(connectorService.send(eq("whatsapp"), any(ConnectorMessage.class))).thenReturn(true);

        var result = api.sendWhatsApp("+447700900000", "Hello",
                "welcome_template", "en");

        assertThat(result.dispatched()).isTrue();
        assertThat(result.connector()).isEqualTo("whatsapp");
    }

    @Test
    void sendWhatsApp_failureReturnsNotDispatched() {
        when(connectorService.send(eq("whatsapp"), any(ConnectorMessage.class)))
                .thenThrow(new RuntimeException("WhatsApp API error"));

        var result = api.sendWhatsApp("+447700900000", "Hello", null, null);

        assertThat(result.dispatched()).isFalse();
        assertThat(result.detail()).isEqualTo("WhatsApp API error");
    }

    @Test
    void sendEmail_dispatchesToEmail() {
        when(connectorService.send(eq("email"), any(ConnectorMessage.class))).thenReturn(true);

        var result = api.sendEmail("user@example.com", "Subject", "Body text");

        assertThat(result.dispatched()).isTrue();
        assertThat(result.connector()).isEqualTo("email");
        assertThat(result.destination()).isEqualTo("user@example.com");
    }

    @Test
    void sendEmail_failureReturnsNotDispatched() {
        when(connectorService.send(eq("email"), any(ConnectorMessage.class)))
                .thenThrow(new RuntimeException("SMTP unreachable"));

        var result = api.sendEmail("user@example.com", "Subject", "Body");

        assertThat(result.dispatched()).isFalse();
        assertThat(result.detail()).isEqualTo("SMTP unreachable");
    }

    @Test
    void sendSlack_dispatchesToSlackWebhook() {
        when(connectorService.send(eq("slack-webhook"), any(ConnectorMessage.class))).thenReturn(true);

        var result = api.sendSlack("https://hooks.slack.com/...", "Alert", "Server is down");

        assertThat(result.dispatched()).isTrue();
        assertThat(result.connector()).isEqualTo("slack-webhook");
    }

    @Test
    void sendSlack_failureReturnsNotDispatched() {
        when(connectorService.send(eq("slack-webhook"), any(ConnectorMessage.class)))
                .thenThrow(new RuntimeException("Webhook rejected"));

        var result = api.sendSlack("https://hooks.slack.com/...", "Alert", "Server is down");

        assertThat(result.dispatched()).isFalse();
        assertThat(result.detail()).isEqualTo("Webhook rejected");
    }

    @Test
    void sendTeams_dispatchesToTeamsWebhook() {
        when(connectorService.send(eq("teams-webhook"), any(ConnectorMessage.class))).thenReturn(true);

        var result = api.sendTeams("https://outlook.office.com/webhook/...", "Alert", "Deploy failed");

        assertThat(result.dispatched()).isTrue();
        assertThat(result.connector()).isEqualTo("teams-webhook");
    }

    @Test
    void sendTeams_failureReturnsNotDispatched() {
        when(connectorService.send(eq("teams-webhook"), any(ConnectorMessage.class)))
                .thenThrow(new RuntimeException("Teams unavailable"));

        var result = api.sendTeams("https://outlook.office.com/webhook/...", "Alert", "Deploy failed");

        assertThat(result.dispatched()).isFalse();
        assertThat(result.detail()).isEqualTo("Teams unavailable");
    }
}
