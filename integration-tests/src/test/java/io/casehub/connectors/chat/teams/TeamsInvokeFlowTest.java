package io.casehub.connectors.chat.teams;

import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import io.casehub.connectors.chat.command.CommandService;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsInvokeFlowTest {

    @Test
    void endToEnd_invokeActivity_dispatchesAndSerializesResponse() {
        var handledRequests = new ArrayList<InvokeRequest>();
        var handler = new InvokeHandler() {
            @Override
            public String name() { return "adaptiveCard/action"; }

            @Override
            public InvokeResponse handle(InvokeRequest request) {
                handledRequests.add(request);
                return new InvokeResponse.Message("Approved by " + request.senderId());
            }
        };
        var connector = buildConnector(new InvokeService(List.of(handler)));

        var result = connector.handle(postRequest(invokeActivity(
                "adaptiveCard/action", "{\"actionVerb\":\"approve\",\"itemId\":42}")));

        assertThat(result).isInstanceOf(WebhookResult.Responded.class);
        var responded = (WebhookResult.Responded) result;

        assertThat(responded.contentType()).isEqualTo("application/json");
        assertThat(responded.messages()).hasSize(1);

        var msg = responded.messages().getFirst();
        assertThat(msg.connectorId()).isEqualTo("teams-inbound");
        assertThat(msg.externalSenderId()).isEqualTo("user-aad-id");
        assertThat(msg.metadata().get("teams-invoke-name")).isEqualTo("adaptiveCard/action");
        assertThat(msg.metadata().get("teams-invoke-data")).contains("approve");

        assertThat(handledRequests).hasSize(1);
        var req = handledRequests.getFirst();
        assertThat(req.name()).isEqualTo("adaptiveCard/action");
        assertThat(req.value().getInt("itemId")).isEqualTo(42);
        assertThat(req.senderId()).isEqualTo("user-aad-id");

        var responseObj = parseJson(responded.responseBody());
        assertThat(responseObj.getInt("statusCode")).isEqualTo(200);
        assertThat(responseObj.getString("type"))
                .isEqualTo("application/vnd.microsoft.activity.message");
        assertThat(responseObj.getString("value"))
                .isEqualTo("Approved by user-aad-id");
    }

    @Test
    void endToEnd_cardUpdateResponse_includesUpdatedCard() {
        var handler = new InvokeHandler() {
            @Override
            public String name() { return "adaptiveCard/action"; }

            @Override
            public InvokeResponse handle(InvokeRequest request) {
                return new InvokeResponse.CardUpdate(
                        "{\"type\":\"AdaptiveCard\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Done\"}]}");
            }
        };
        var connector = buildConnector(new InvokeService(List.of(handler)));

        var result = connector.handle(postRequest(invokeActivity(
                "adaptiveCard/action", "{}")));

        var responded = (WebhookResult.Responded) result;
        var responseObj = parseJson(responded.responseBody());
        assertThat(responseObj.getInt("statusCode")).isEqualTo(200);
        assertThat(responseObj.getString("type"))
                .isEqualTo("application/vnd.microsoft.card.adaptive");
        assertThat(responseObj.getJsonObject("value").getString("type")).isEqualTo("AdaptiveCard");
    }

    @Test
    void endToEnd_noMatchingHandler_returnsAccepted() {
        var connector = buildConnector(new InvokeService(List.of()));

        var result = connector.handle(postRequest(invokeActivity(
                "unknown/action", "{}")));

        var responded = (WebhookResult.Responded) result;
        assertThat(responded.messages()).hasSize(1);

        var responseObj = parseJson(responded.responseBody());
        assertThat(responseObj.getInt("statusCode")).isEqualTo(200);
        assertThat(responseObj.size()).isEqualTo(1);
    }

    @Test
    void endToEnd_handlerThrows_returnsAcceptedGracefully() {
        var handler = new InvokeHandler() {
            @Override
            public String name() { return "boom"; }

            @Override
            public InvokeResponse handle(InvokeRequest request) {
                throw new RuntimeException("Handler exploded");
            }
        };
        var connector = buildConnector(new InvokeService(List.of(handler)));

        var result = connector.handle(postRequest(invokeActivity("boom", "{}")));

        var responded = (WebhookResult.Responded) result;
        var responseObj = parseJson(responded.responseBody());
        assertThat(responseObj.getInt("statusCode")).isEqualTo(200);
        assertThat(responseObj.size()).isEqualTo(1);
    }

    private static TeamsInboundConnector buildConnector(InvokeService invokeService) {
        var auth = new BotFrameworkAuth("test-app-id", true,
                "https://login.botframework.com/v1/.well-known/openidconfiguration",
                "https://api.botframework.com");
        var client = new BotFrameworkClient(null,
                "*.botframework.com,smba.trafficmanager.net");
        var commandService = new CommandService(List.of(), List.of());
        return new TeamsInboundConnector(auth, client, commandService,
                "test-app-id", null, invokeService);
    }

    private static WebhookRequest postRequest(String body) {
        return new WebhookRequest(body, Map.of(), Map.of(), HttpMethod.POST,
                "https://test.example.com/connectors/teams-inbound/webhook");
    }

    private static String invokeActivity(String name, String valueJson) {
        return """
                {"type":"invoke","name":"%s","value":%s,\
                "from":{"id":"user-aad-id","aadObjectId":"user-aad-id","name":"Test User"},\
                "conversation":{"id":"conv-1"},\
                "channelData":{"tenant":{"id":"tenant-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/uk/",\
                "timestamp":"2026-11-16T10:00:00Z","id":"act-1"}""".formatted(name, valueJson);
    }

    private static JsonObject parseJson(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
