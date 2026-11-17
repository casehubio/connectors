package io.casehub.connectors.chat.teams;

import io.casehub.platform.api.authn.ServiceAccessToken;
import io.casehub.platform.api.authn.ServiceConnection;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class InvokeServiceCdiTest {

    @Inject
    InvokeService invokeService;

    @Test
    void cdiDiscoversApplicationScopedHandlers() {
        var request = new InvokeRequest("test/echo", Json.createObjectBuilder().build(),
                                        "user-1", "channel-1", java.util.Map.of());

        InvokeResponse response = invokeService.dispatch(request);

        assertThat(response).isInstanceOf(InvokeResponse.Message.class);
        assertThat(((InvokeResponse.Message) response).text()).isEqualTo("echo:test/echo");
    }

    @Test
    void fullDispatchChain_handlerThroughSerializer() {
        var request = new InvokeRequest("test/echo", Json.createObjectBuilder().build(),
                                        "user-1", "channel-1", java.util.Map.of());

        InvokeResponse response = invokeService.dispatch(request);
        String         json     = InvokeResponseSerializer.toJson(response);

        JsonObject obj = Json.createReader(new StringReader(json)).readObject();
        assertThat(obj.getInt("statusCode")).isEqualTo(200);
        assertThat(obj.getString("type")).isEqualTo("application/vnd.microsoft.activity.message");
        assertThat(obj.getString("value")).isEqualTo("echo:test/echo");
    }

    @Test
    void cdiDiscoversMultipleHandlers() {
        var request = new InvokeRequest("test/card", Json.createObjectBuilder().build(),
                                        "user-1", "channel-1", java.util.Map.of());

        InvokeResponse response = invokeService.dispatch(request);

        assertThat(response).isInstanceOf(InvokeResponse.CardUpdate.class);
    }

    @Test
    void unknownInvokeName_returnsAccepted() {
        var request = new InvokeRequest("no/such/handler", Json.createObjectBuilder().build(),
                                        "user-1", "channel-1", java.util.Map.of());

        InvokeResponse response = invokeService.dispatch(request);

        assertThat(response).isInstanceOf(InvokeResponse.Accepted.class);
    }

    @Test
    void unknownInvokeName_serializesToStatusCode200() {
        var request = new InvokeRequest("no/such/handler", Json.createObjectBuilder().build(),
                                        "user-1", "channel-1", java.util.Map.of());

        String json = InvokeResponseSerializer.toJson(invokeService.dispatch(request));

        JsonObject obj = Json.createReader(new StringReader(json)).readObject();
        assertThat(obj.getInt("statusCode")).isEqualTo(200);
        assertThat(obj.containsKey("type")).isFalse();
    }

    @ApplicationScoped
    public static class TestEchoHandler implements InvokeHandler {
        @Override
        public String name() {
            return "test/echo";
        }

        @Override
        public InvokeResponse handle(InvokeRequest request) {
            return new InvokeResponse.Message("echo:" + request.name());
        }
    }

    @ApplicationScoped
    public static class TestCardHandler implements InvokeHandler {
        @Override
        public String name() {
            return "test/card";
        }

        @Override
        public InvokeResponse handle(InvokeRequest request) {
            return new InvokeResponse.CardUpdate("{\"type\":\"AdaptiveCard\",\"body\":[]}");
        }
    }

    @ApplicationScoped
    public static class StubServiceConnectionProvider implements ServiceConnectionProvider {
        @Override
        public ServiceConnection getConnection(String actorId, String provider, String tenancyId) {
            return null;
        }

        @Override
        public List<ServiceConnection> listConnections(String actorId, String tenancyId) {
            return List.of();
        }

        @Override
        public ServiceAccessToken getAccessToken(String actorId, String provider, String tenancyId) {
            return null;
        }

        @Override
        public java.util.Set<String> missingScopes(String actorId, String provider, String tenancyId) {
            return java.util.Set.of();
        }
    }
}
