package io.casehub.connectors.telegram.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        @JsonProperty("update_id") long updateId,
        TelegramMessage message,
        @JsonProperty("edited_message") TelegramMessage editedMessage,
        @JsonProperty("callback_query") TelegramCallbackQuery callbackQuery) {}
