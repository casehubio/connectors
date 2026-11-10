package io.casehub.connectors.chat.steps;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import java.util.LinkedHashMap;

@Plugin(value = "chat-send", description = "Send a chat message with optional attachment", absorbSiblings = true)
public record ChatSendPlugin(
        @Required String channel,
        @Required String message,
        @Optional byte[] attachment,
        @Optional String attachmentType,
        @Optional String attachmentName) {

    @Execute
    public Result execute(ChatPlatform chatPlatform) {
        var         ref = new ChatChannelRef(channel);
        ChatContent content;
        if (attachment != null && attachment.length > 0) {
            var att = new io.casehub.connectors.Attachment(
                    attachmentName, attachmentType != null ? attachmentType : "application/octet-stream", attachment);
            content = new ChatContent(message, null, java.util.List.of(att), java.util.List.of());
        } else {
            content = new ChatContent(message);
        }
        var result = chatPlatform.messaging().send(ref, content);
        var output = new LinkedHashMap<String, Object>();
        output.put("ok", result.ok());
        output.put("channel", channel);
        if (result.error() != null) {
            output.put("error", result.error());
        }
        return Result.of(output);
    }
}
