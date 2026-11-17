package io.casehub.connectors.chat.teams.ref;

import io.casehub.connectors.chat.teams.ComposeAttachment;
import io.casehub.connectors.chat.teams.InvokeHandler;
import io.casehub.connectors.chat.teams.InvokeRequest;
import io.casehub.connectors.chat.teams.InvokeResponse;

import java.util.List;

public class RefSearchHandler implements InvokeHandler {

    private static final String CARD_1 = "{\"type\":\"AdaptiveCard\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Contoso Widget\",\"weight\":\"bolder\"},{\"type\":\"TextBlock\",\"text\":\"A premium widget for all your needs.\"}]}";
    private static final String CARD_2 = "{\"type\":\"AdaptiveCard\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Northwind Gadget\",\"weight\":\"bolder\"},{\"type\":\"TextBlock\",\"text\":\"The essential gadget for modern teams.\"}]}";
    private static final String CARD_3 = "{\"type\":\"AdaptiveCard\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Adventure Gizmo\",\"weight\":\"bolder\"},{\"type\":\"TextBlock\",\"text\":\"Take your projects to the next level.\"}]}";

    @Override
    public String name() {
        return "composeExtension/query";
    }

    @Override
    public InvokeResponse handle(InvokeRequest request) {
        return new InvokeResponse.ComposeResult("list", List.of(
                ComposeAttachment.adaptive(CARD_1).preview("Contoso Widget", "£29.99").build(),
                ComposeAttachment.adaptive(CARD_2).preview("Northwind Gadget", "£49.99").build(),
                ComposeAttachment.adaptive(CARD_3).preview("Adventure Gizmo", "£19.99").build()
        ));
    }
}
