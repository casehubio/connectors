package io.casehub.connectors.chat.teams.ref;

import io.casehub.connectors.chat.teams.ComposeAttachment;
import io.casehub.connectors.chat.teams.InvokeHandler;
import io.casehub.connectors.chat.teams.InvokeRequest;
import io.casehub.connectors.chat.teams.InvokeResponse;

import java.util.List;

public class RefSubmitActionHandler implements InvokeHandler {

    private static final String CONFIRMATION_CARD = "{\"type\":\"AdaptiveCard\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Item Created Successfully\",\"weight\":\"bolder\"},{\"type\":\"TextBlock\",\"text\":\"Your item has been created and is ready to use.\"}]}";

    @Override
    public String name() {
        return "composeExtension/submitAction";
    }

    @Override
    public InvokeResponse handle(InvokeRequest request) {
        return new InvokeResponse.ComposeResult("list", List.of(
                ComposeAttachment.adaptive(CONFIRMATION_CARD)
                        .preview("Item Created", "Action completed")
                        .build()
        ));
    }
}
