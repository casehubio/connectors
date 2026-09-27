package io.casehub.connectors.chat.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.command.CommandInvocation;
import io.casehub.connectors.chat.command.CommandResponse;
import io.casehub.connectors.chat.command.CommandService;

class DiscordInteractionEndpointTest {

    private KeyPair keyPair;
    private DiscordInteractionEndpoint endpoint;
    private ObjectMapper mapper;
    private StubCommandService commandService;

    @BeforeEach
    void setUp() throws Exception {
        var kpg = KeyPairGenerator.getInstance("Ed25519");
        keyPair = kpg.generateKeyPair();
        mapper = new ObjectMapper();

        commandService = new StubCommandService();
        endpoint = new DiscordInteractionEndpoint(
                commandService, null, "APP123",
                keyPair.getPublic(), (java.util.concurrent.ExecutorService) null);
    }

    private String sign(String timestamp, String body) throws Exception {
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update((timestamp + body).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(signer.sign());
    }

    @Test
    void pingReturnsPong() throws Exception {
        var body = "{\"type\":1}";
        var timestamp = "1234567890";
        var sig = sign(timestamp, body);

        var response = endpoint.handleInteraction(sig, timestamp, body);
        assertThat(response.getStatus()).isEqualTo(200);

        var responseBody = (String) response.getEntity();
        assertThat(responseBody).contains("\"type\":1");
    }

    @Test
    void invalidSignatureReturns401() {
        var body = "{\"type\":1}";
        var response = endpoint.handleInteraction(
                "bad", "1234567890", body);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void missingPublicKeyReturns503() {
        var noKeyEndpoint = new DiscordInteractionEndpoint(
                commandService, null, "APP123",
                (java.security.PublicKey) null,
                (java.util.concurrent.ExecutorService) null);
        var response = noKeyEndpoint.handleInteraction(
                "sig", "ts", "{\"type\":1}");
        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void applicationCommandDispatchesAndReturnsImmediate() throws Exception {
        commandService.setResponse(
                new CommandResponse.Immediate("pong", false));

        var body = buildCommandPayload("ping", Map.of());
        var timestamp = "1234567890";
        var sig = sign(timestamp, body);

        var response = endpoint.handleInteraction(sig, timestamp, body);
        assertThat(response.getStatus()).isEqualTo(200);

        var responseBody = (String) response.getEntity();
        assertThat(responseBody).contains("\"type\":4");
        assertThat(responseBody).contains("pong");
    }

    @Test
    void ephemeralResponseIncludesFlags() throws Exception {
        commandService.setResponse(
                new CommandResponse.Immediate("secret", true));

        var body = buildCommandPayload("ping", Map.of());
        var timestamp = "1234567890";
        var sig = sign(timestamp, body);

        var response = endpoint.handleInteraction(sig, timestamp, body);
        var responseBody = (String) response.getEntity();
        assertThat(responseBody).contains("\"flags\":64");
    }

    @Test
    void commandArgumentsParsed() throws Exception {
        commandService.setResponse(
                new CommandResponse.Immediate("ok"));
        commandService.setCaptureInvocation(true);

        var body = buildCommandPayload("assign", Map.of("user", "alice"));
        var timestamp = "1234567890";
        var sig = sign(timestamp, body);

        endpoint.handleInteraction(sig, timestamp, body);

        assertThat(commandService.lastInvocation().commandName())
                .isEqualTo("assign");
        assertThat(commandService.lastInvocation().argument("user"))
                .isEqualTo("alice");
        assertThat(commandService.lastInvocation().platformId())
                .isEqualTo("discord");
    }

    private String buildCommandPayload(
            String commandName, Map<String, String> options) {
        var node = mapper.createObjectNode();
        node.put("type", 2);
        node.put("id", "interaction-1");
        node.put("token", "int-token");
        node.put("channel_id", "ch1");
        node.put("guild_id", "guild1");
        var member = node.putObject("member");
        var user = member.putObject("user");
        user.put("id", "user1");
        var data = node.putObject("data");
        data.put("name", commandName);
        data.put("type", 1);
        if (!options.isEmpty()) {
            var opts = data.putArray("options");
            options.forEach((k, v) -> {
                var opt = opts.addObject();
                opt.put("name", k);
                opt.put("value", v);
            });
        }
        return node.toString();
    }

    static class StubCommandService extends CommandService {
        private CommandResponse response =
                new CommandResponse.Immediate("default");
        private boolean captureInvocation;
        private CommandInvocation lastInvocation;

        StubCommandService() {
            super(List.of(), List.of());
        }

        void setResponse(CommandResponse r) { this.response = r; }
        void setCaptureInvocation(boolean b) { captureInvocation = b; }
        CommandInvocation lastInvocation() { return lastInvocation; }

        @Override
        public CommandResponse dispatch(
                String commandName, CommandInvocation invocation) {
            if (captureInvocation) lastInvocation = invocation;
            return response;
        }
    }
}
