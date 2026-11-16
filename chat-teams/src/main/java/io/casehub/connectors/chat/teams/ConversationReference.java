package io.casehub.connectors.chat.teams;

public record ConversationReference(
    String aadObjectId,
    String conversationId,
    String serviceUrl,
    String tenantId
) {}
