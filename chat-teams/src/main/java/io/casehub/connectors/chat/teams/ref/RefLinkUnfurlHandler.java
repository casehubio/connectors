package io.casehub.connectors.chat.teams.ref;

import io.casehub.connectors.chat.teams.ComposeAttachment;
import io.casehub.connectors.chat.teams.InvokeHandler;
import io.casehub.connectors.chat.teams.InvokeRequest;
import io.casehub.connectors.chat.teams.InvokeResponse;

import java.util.List;

public class RefLinkUnfurlHandler implements InvokeHandler {

    @Override
    public String name() {
        return "composeExtension/queryLink";
    }

    @Override
    public InvokeResponse handle(InvokeRequest request) {
        return new InvokeResponse.ComposeResult("list", List.of(
                ComposeAttachment.hero("Link Preview")
                                 .text("This is a preview of the linked content.")
                                 .button("openUrl", "View", "https://example.com")
                                 .build()
                                                               ));
    }
}
