package io.casehub.connectors.chat.teams;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import java.io.StringReader;

class InvokeResponseSerializer {

    static String toJson(InvokeResponse response) {
        return switch (response) {
            case InvokeResponse.Accepted() ->
                Json.createObjectBuilder()
                    .add("statusCode", 200)
                    .build().toString();

            case InvokeResponse.CardUpdate(var cardJson) ->
                Json.createObjectBuilder()
                    .add("statusCode", 200)
                    .add("type", "application/vnd.microsoft.card.adaptive")
                    .add("value", parseJson(cardJson))
                    .build().toString();

            case InvokeResponse.Message(var text) ->
                Json.createObjectBuilder()
                    .add("statusCode", 200)
                    .add("type", "application/vnd.microsoft.activity.message")
                    .add("value", text)
                    .build().toString();

            case InvokeResponse.TaskModuleContinue(var title, var cardJson) ->
                Json.createObjectBuilder()
                    .add("task", Json.createObjectBuilder()
                        .add("type", "continue")
                        .add("value", Json.createObjectBuilder()
                            .add("title", title)
                            .add("card", Json.createObjectBuilder()
                                .add("contentType", "application/vnd.microsoft.card.adaptive")
                                .add("content", parseJson(cardJson)))))
                    .build().toString();

            case InvokeResponse.TaskModuleMessage(var text) ->
                Json.createObjectBuilder()
                    .add("task", Json.createObjectBuilder()
                        .add("type", "message")
                        .add("value", text))
                    .build().toString();

            case InvokeResponse.Error(var statusCode, var message) ->
                Json.createObjectBuilder()
                    .add("statusCode", statusCode)
                    .add("type", "application/vnd.microsoft.error")
                    .add("value", Json.createObjectBuilder()
                        .add("code", "Error")
                        .add("message", message))
                    .build().toString();
        };
    }

    private static JsonObject parseJson(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
