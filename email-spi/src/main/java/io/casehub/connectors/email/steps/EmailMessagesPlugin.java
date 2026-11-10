package io.casehub.connectors.email.steps;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.email.spi.EmailPlatform;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

@Plugin(value = "email-messages", description = "List messages in a mailbox")
public record EmailMessagesPlugin(@Required String mailbox) {

    @Execute
    public Result execute(EmailPlatform emailPlatform) {
        var page = emailPlatform.listMessages(mailbox, Instant.EPOCH, Instant.now(),
                PageRequest.first(20));
        var items = page.items().stream()
                .map(msg -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("id", msg.id());
                    m.put("subject", msg.subject());
                    m.put("from", msg.from());
                    m.put("read", msg.read());
                    return (Map<String, Object>) m;
                })
                .toList();
        var output = new LinkedHashMap<String, Object>();
        output.put("items", items);
        output.put("count", items.size());
        return Result.of(output);
    }
}
