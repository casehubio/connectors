package io.casehub.connectors.telegram.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
        @JsonProperty("message_id") long messageId,
        TelegramChat chat,
        TelegramUser from,
        String text,
        long date,
        @JsonProperty("reply_to_message") TelegramMessage replyToMessage) {}
