package io.casehub.connectors.telegram.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramCallbackQuery(
        String id,
        TelegramUser from,
        TelegramMessage message,
        String data) {}
