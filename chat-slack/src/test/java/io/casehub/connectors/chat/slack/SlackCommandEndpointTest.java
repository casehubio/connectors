package io.casehub.connectors.chat.slack;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.command.CommandInvocation;
import io.casehub.connectors.chat.command.CommandResponse;
import io.casehub.connectors.chat.command.CommandService;

class SlackCommandEndpointTest {

    private static final String SIGNING_SECRET = "test-signing-secret";
    private SlackCommandEndpoint endpoint;
    private StubCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new StubCommandService();
        endpoint = new SlackCommandEndpoint(
                commandService, SIGNING_SECRET, null);
    }

    private String sign(String timestamp, String body) throws Exception {
        String basestring = "v0:" + timestamp + ":" + body;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                SIGNING_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        byte[] hash = mac.doFinal(
                basestring.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder("v0=");
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Test
    void validSignatureDispatchesCommand() throws Exception {
        commandService.setResponse(
                new CommandResponse.Immediate("pong"));

        String body = "command=%2Fping&text=&user_id=U123"
                + "&channel_id=C456&team_id=T789"
                + "&team_domain=test&response_url=https%3A%2F%2Fhooks.slack.com%2Fcommands%2Ffoo";
        String timestamp = String.valueOf(
                System.currentTimeMillis() / 1000);
        String sig = sign(timestamp, body);

        var response = endpoint.handleCommand(sig, timestamp, body);
        assertThat(response.getStatus()).isEqualTo(200);

        var responseBody = (String) response.getEntity();
        assertThat(responseBody).contains("pong");
    }

    @Test
    void invalidSignatureReturns401() {
        String body = "command=%2Fping&text=";
        var response = endpoint.handleCommand(
                "v0=bad", "1234567890", body);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void staleTimestampReturns401() throws Exception {
        String body = "command=%2Fping&text=";
        String staleTimestamp = String.valueOf(
                (System.currentTimeMillis() / 1000) - 600);
        String sig = sign(staleTimestamp, body);

        var response = endpoint.handleCommand(
                sig, staleTimestamp, body);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void commandTextPassedAsArgument() throws Exception {
        commandService.setResponse(
                new CommandResponse.Immediate("ok"));
        commandService.setCaptureInvocation(true);

        String body = "command=%2Fassign&text=user123+high"
                + "&user_id=U123&channel_id=C456"
                + "&team_id=T789&team_domain=test"
                + "&response_url=https%3A%2F%2Fhooks.slack.com";
        String timestamp = String.valueOf(
                System.currentTimeMillis() / 1000);
        String sig = sign(timestamp, body);

        endpoint.handleCommand(sig, timestamp, body);

        assertThat(commandService.lastInvocation().argument("text"))
                .isEqualTo("user123 high");
        assertThat(commandService.lastInvocation().commandName())
                .isEqualTo("assign");
        assertThat(commandService.lastInvocation().platformId())
                .isEqualTo("slack");
    }

    @Test
    void missingSigningSecretReturns503() {
        var noSecretEndpoint = new SlackCommandEndpoint(
                commandService, "", null);
        var response = noSecretEndpoint.handleCommand(
                "sig", "ts", "command=%2Fping");
        assertThat(response.getStatus()).isEqualTo(503);
    }

    static class StubCommandService extends CommandService {
        private CommandResponse response =
                new CommandResponse.Immediate("default");
        private boolean captureInvocation;
        private CommandInvocation lastInvocation;

        StubCommandService() { super(List.of(), List.of()); }

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
