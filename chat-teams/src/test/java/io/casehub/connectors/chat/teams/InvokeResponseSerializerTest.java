package io.casehub.connectors.chat.teams;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

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

    private static JsonObject parse(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
