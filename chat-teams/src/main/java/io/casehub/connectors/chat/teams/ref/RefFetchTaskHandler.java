package io.casehub.connectors.chat.teams.ref;

import io.casehub.connectors.chat.teams.InvokeHandler;
import io.casehub.connectors.chat.teams.InvokeRequest;
import io.casehub.connectors.chat.teams.InvokeResponse;

public class RefFetchTaskHandler implements InvokeHandler {

    private static final String FORM_CARD = "{\"type\":\"AdaptiveCard\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Create a new item\",\"weight\":\"bolder\"},{\"type\":\"Input.Text\",\"id\":\"title\",\"label\":\"Title\",\"placeholder\":\"Enter a title\"}],\"actions\":[{\"type\":\"Action.Submit\",\"title\":\"Create\"}]}";

    @Override
    public String name() {
        return "composeExtension/fetchTask";
    }

    @Override
    public InvokeResponse handle(InvokeRequest request) {
        return new InvokeResponse.ComposeContinue("Create Item", FORM_CARD);
    }
}
