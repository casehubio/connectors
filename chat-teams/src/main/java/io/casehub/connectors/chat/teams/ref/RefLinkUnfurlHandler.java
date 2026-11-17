package io.casehub.connectors.chat.teams.ref;

import io.casehub.connectors.chat.teams.ComposeAttachment;
import io.casehub.connectors.chat.teams.InvokeHandler;
import io.casehub.connectors.chat.teams.InvokeRequest;
import io.casehub.connectors.chat.teams.InvokeResponse;

import java.util.List;

public class RefLinkUnfurlHandler implements InvokeHandler {

    private static final String UNFURL_CARD = "{\"type\":\"AdaptiveCard\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Link Preview\",\"weight\":\"bolder\"},{\"type\":\"TextBlock\",\"text\":\"This is a preview of the linked content.\"}],\"actions\":[{\"type\":\"Action.OpenUrl\",\"title\":\"View\",\"url\":\"https://example.com\"}]}";

    @Override
    public String name() {
        return "composeExtension/queryLink";
    }

    @Override
    public InvokeResponse handle(InvokeRequest request) {
        return new InvokeResponse.ComposeResult("list", List.of(
                ComposeAttachment.adaptive(UNFURL_CARD).build()
        ));
    }
}
