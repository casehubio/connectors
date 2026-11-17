package io.casehub.connectors.chat.teams;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvokeResponseSerializerTest {

    @Test
    void accepted_returnsStatusCode200() {
        String json = InvokeResponseSerializer.toJson(new InvokeResponse.Accepted());
        JsonObject obj = parse(json);
        assertThat(obj.getInt("statusCode")).isEqualTo(200);
        assertThat(obj.size()).isEqualTo(1);
    }

    @Test
    void cardUpdate_returnsAdaptiveCardType() {
        String cardJson = "{\"type\":\"AdaptiveCard\",\"body\":[]}";
        String json = InvokeResponseSerializer.toJson(new InvokeResponse.CardUpdate(cardJson));
        JsonObject obj = parse(json);
        assertThat(obj.getInt("statusCode")).isEqualTo(200);
        assertThat(obj.getString("type")).isEqualTo("application/vnd.microsoft.card.adaptive");
        assertThat(obj.getJsonObject("value").getString("type")).isEqualTo("AdaptiveCard");
    }

    @Test
    void message_returnsActivityMessageType() {
        String json = InvokeResponseSerializer.toJson(new InvokeResponse.Message("Done!"));
        JsonObject obj = parse(json);
        assertThat(obj.getInt("statusCode")).isEqualTo(200);
        assertThat(obj.getString("type")).isEqualTo("application/vnd.microsoft.activity.message");
        assertThat(obj.getString("value")).isEqualTo("Done!");
    }

    @Test
    void taskModuleContinue_returnsTaskContinueEnvelope() {
        String cardJson = "{\"type\":\"AdaptiveCard\",\"body\":[]}";
        String json = InvokeResponseSerializer.toJson(
                new InvokeResponse.TaskModuleContinue("My Task", cardJson));
        JsonObject obj = parse(json);
        JsonObject task = obj.getJsonObject("task");
        assertThat(task.getString("type")).isEqualTo("continue");
        JsonObject value = task.getJsonObject("value");
        assertThat(value.getString("title")).isEqualTo("My Task");
        JsonObject card = value.getJsonObject("card");
        assertThat(card.getString("contentType")).isEqualTo("application/vnd.microsoft.card.adaptive");
        assertThat(card.getJsonObject("content").getString("type")).isEqualTo("AdaptiveCard");
    }

    @Test
    void taskModuleMessage_returnsTaskMessageEnvelope() {
        String json = InvokeResponseSerializer.toJson(
                new InvokeResponse.TaskModuleMessage("Thanks for submitting!"));
        JsonObject obj = parse(json);
        JsonObject task = obj.getJsonObject("task");
        assertThat(task.getString("type")).isEqualTo("message");
        assertThat(task.getString("value")).isEqualTo("Thanks for submitting!");
    }

    @Test
    void error_returnsErrorType() {
        String json = InvokeResponseSerializer.toJson(
                new InvokeResponse.Error(400, "Invalid input"));
        JsonObject obj = parse(json);
        assertThat(obj.getInt("statusCode")).isEqualTo(400);
        assertThat(obj.getString("type")).isEqualTo("application/vnd.microsoft.error");
        JsonObject value = obj.getJsonObject("value");
        assertThat(value.getString("code")).isEqualTo("Error");
        assertThat(value.getString("message")).isEqualTo("Invalid input");
    }

    @Test
    void composeResult_serializesWithAttachments() {
        String att1 = "{\"contentType\":\"application/vnd.microsoft.card.adaptive\",\"content\":{\"type\":\"AdaptiveCard\"},\"preview\":{\"contentType\":\"application/vnd.microsoft.card.thumbnail\",\"content\":{\"title\":\"Result 1\"}}}";
        String att2 = "{\"contentType\":\"application/vnd.microsoft.card.adaptive\",\"content\":{\"type\":\"AdaptiveCard\"},\"preview\":{\"contentType\":\"application/vnd.microsoft.card.thumbnail\",\"content\":{\"title\":\"Result 2\"}}}";
        String json = InvokeResponseSerializer.toJson(
                new InvokeResponse.ComposeResult("list", List.of(att1, att2)));
        JsonObject obj = parse(json);
        JsonObject ext = obj.getJsonObject("composeExtension");
        assertThat(ext.getString("type")).isEqualTo("result");
        assertThat(ext.getString("attachmentLayout")).isEqualTo("list");
        assertThat(ext.getJsonArray("attachments")).hasSize(2);
        assertThat(ext.getJsonArray("attachments").getJsonObject(0)
                      .getJsonObject("preview").getJsonObject("content").getString("title"))
                .isEqualTo("Result 1");
    }

    @Test
    void composeResult_emptyAttachments() {
        String json = InvokeResponseSerializer.toJson(
                new InvokeResponse.ComposeResult("list", List.of()));
        JsonObject obj = parse(json);
        JsonObject ext = obj.getJsonObject("composeExtension");
        assertThat(ext.getString("type")).isEqualTo("result");
        assertThat(ext.getJsonArray("attachments")).isEmpty();
    }

    @Test
    void composeResult_gridLayout() {
        String att = "{\"contentType\":\"application/vnd.microsoft.card.adaptive\",\"content\":{\"type\":\"AdaptiveCard\"}}";
        String json = InvokeResponseSerializer.toJson(
                new InvokeResponse.ComposeResult("grid", List.of(att)));
        JsonObject obj = parse(json);
        assertThat(obj.getJsonObject("composeExtension").getString("attachmentLayout"))
                .isEqualTo("grid");
    }

    @Test
    void composeAuth_serializesWithSuggestedActions() {
        String actions = "{\"actions\":[{\"type\":\"openUrl\",\"value\":\"https://auth.example.com/oauth\",\"title\":\"Sign in\"}]}";
        String json = InvokeResponseSerializer.toJson(
                new InvokeResponse.ComposeAuth(actions));
        JsonObject obj = parse(json);
        JsonObject ext = obj.getJsonObject("composeExtension");
        assertThat(ext.getString("type")).isEqualTo("auth");
        assertThat(ext.getJsonObject("suggestedActions")
                      .getJsonArray("actions").getJsonObject(0)
                      .getString("type")).isEqualTo("openUrl");
    }

    @Test
    void composeContinue_serializesWithTitleAndCard() {
        String cardJson = "{\"type\":\"AdaptiveCard\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Enter details\"}]}";
        String json = InvokeResponseSerializer.toJson(
                new InvokeResponse.ComposeContinue("Action Form", cardJson));
        JsonObject obj = parse(json);
        JsonObject ext = obj.getJsonObject("composeExtension");
        assertThat(ext.getString("type")).isEqualTo("continue");
        JsonObject value = ext.getJsonObject("value");
        assertThat(value.getString("title")).isEqualTo("Action Form");
        JsonObject card = value.getJsonObject("card");
        assertThat(card.getString("contentType")).isEqualTo("application/vnd.microsoft.card.adaptive");
        assertThat(card.getJsonObject("content").getString("type")).isEqualTo("AdaptiveCard");
    }


    private static JsonObject parse(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
