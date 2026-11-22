package io.casehub.connectors.telegram.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramChat(
        long id,
        String type,
        String title,
        String description,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String username) {}
