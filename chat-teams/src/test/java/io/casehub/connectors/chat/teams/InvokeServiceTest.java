package io.casehub.connectors.chat.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class InvokeServiceTest {

    private static final JsonObject EMPTY_VALUE = Json.createObjectBuilder().build();

    @Test
    void dispatch_withHandler_delegatesToHandler() {
        var handler = stubHandler("adaptiveCard/action", new InvokeResponse.Message("OK"));
        var service = new InvokeService(List.of(handler));

        var request = new InvokeRequest("adaptiveCard/action", EMPTY_VALUE,
                "user1", "channel1", Map.of());
        InvokeResponse result = service.dispatch(request);

        assertThat(result).isInstanceOf(InvokeResponse.Message.class);
        assertThat(((InvokeResponse.Message) result).text()).isEqualTo("OK");
    }

    @Test
    void dispatch_noHandler_returnsAccepted() {
        var service = new InvokeService(List.of());

        var request = new InvokeRequest("unknown/action", EMPTY_VALUE,
                "user1", "channel1", Map.of());
        InvokeResponse result = service.dispatch(request);

        assertThat(result).isInstanceOf(InvokeResponse.Accepted.class);
    }

    @Test
    void dispatch_handlerThrows_returnsAccepted() {
        InvokeHandler throwing = new InvokeHandler() {
            @Override public String name() { return "adaptiveCard/action"; }
            @Override public InvokeResponse handle(InvokeRequest r) {
                throw new RuntimeException("boom");
            }
        };
        var service = new InvokeService(List.of(throwing));

        var request = new InvokeRequest("adaptiveCard/action", EMPTY_VALUE,
                "user1", "channel1", Map.of());
        InvokeResponse result = service.dispatch(request);

        assertThat(result).isInstanceOf(InvokeResponse.Accepted.class);
    }

    @Test
    void constructor_duplicateHandlerName_throws() {
        var h1 = stubHandler("adaptiveCard/action", new InvokeResponse.Accepted());
        var h2 = stubHandler("adaptiveCard/action", new InvokeResponse.Accepted());

        assertThatThrownBy(() -> new InvokeService(List.of(h1, h2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adaptiveCard/action");
    }

    @Test
    void dispatch_multipleHandlers_routesByName() {
        var cardHandler = stubHandler("adaptiveCard/action", new InvokeResponse.CardUpdate("{}"));
        var taskHandler = stubHandler("task/fetch", new InvokeResponse.TaskModuleMessage("Hi"));
        var service = new InvokeService(List.of(cardHandler, taskHandler));

        var cardRequest = new InvokeRequest("adaptiveCard/action", EMPTY_VALUE,
                "user1", "channel1", Map.of());
        assertThat(service.dispatch(cardRequest)).isInstanceOf(InvokeResponse.CardUpdate.class);

        var taskRequest = new InvokeRequest("task/fetch", EMPTY_VALUE,
                "user1", "channel1", Map.of());
        assertThat(service.dispatch(taskRequest)).isInstanceOf(InvokeResponse.TaskModuleMessage.class);
    }

    private static InvokeHandler stubHandler(String name, InvokeResponse response) {
        return new InvokeHandler() {
            @Override public String name() { return name; }
            @Override public InvokeResponse handle(InvokeRequest r) { return response; }
        };
    }
}
