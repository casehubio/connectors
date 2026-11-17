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
        String queryText = request.value().getString("queryText", "");
        record Item(String cardJson, String title, String price) {}
        var allItems = List.of(
                new Item(CARD_1, "Contoso Widget", "£29.99"),
                new Item(CARD_2, "Northwind Gadget", "£49.99"),
                new Item(CARD_3, "Adventure Gizmo", "£19.99"));
        var attachments = allItems.stream()
                                  .filter(item -> queryText.isBlank()
                                                  || item.title().toLowerCase().contains(queryText.toLowerCase()))
                                  .map(item -> ComposeAttachment.adaptive(item.cardJson())
                                                                .preview(item.title(), item.price()).build())
                                  .toList();
        return new InvokeResponse.ComposeResult("list", attachments);
    }
}
