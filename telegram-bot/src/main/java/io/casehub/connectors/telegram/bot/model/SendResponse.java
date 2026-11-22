package io.casehub.connectors.telegram.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SendResponse(boolean ok, TelegramMessage result) {}
