package io.casehub.connectors.chat.slack;

import io.casehub.connectors.chat.command.CommandInvocation;
import io.casehub.connectors.chat.command.CommandResponse;
import io.casehub.connectors.chat.command.CommandService;
import io.casehub.connectors.chat.command.DeferredReply;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/interactions/slack")
@ApplicationScoped
public class SlackCommandEndpoint {

    private static final Logger LOG =
            Logger.getLogger(SlackCommandEndpoint.class.getName());
    private static final long MAX_TIMESTAMP_AGE_SECONDS = 300;
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final CommandService commandService;
    private final String signingSecret;
    private final ExecutorService executor;

    @Inject
    SlackCommandEndpoint(
            CommandService commandService,
            @ConfigProperty(name = "casehub.slack.signing-secret",
                    defaultValue = "") String signingSecret,
            org.eclipse.microprofile.context.ManagedExecutor executor) {
        this.commandService = commandService;
        this.signingSecret = signingSecret;
        this.executor = executor;
    }

    SlackCommandEndpoint(
            CommandService commandService,
            String signingSecret,
            ExecutorService executor) {
        this.commandService = commandService;
        this.signingSecret = signingSecret;
        this.executor = executor;
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleCommand(
            @HeaderParam("X-Slack-Signature") String signature,
            @HeaderParam("X-Slack-Request-Timestamp") String timestamp,
            String body) {

        if (signingSecret.isBlank()) {
            return Response.status(503).build();
        }

        if (!verifyTimestamp(timestamp)
                || !verifySignature(signature, timestamp, body)) {
            return Response.status(401).build();
        }

        try {
            Map<String, String> params = parseFormBody(body);

            String command = params.getOrDefault("command", "")
                    .replaceFirst("^/", "");
            String text = params.getOrDefault("text", "");

            Map<String, String> arguments = text.isBlank()
                    ? Map.of()
                    : Map.of("text", text);

            String responseUrl = params.getOrDefault(
                    "response_url", "");

            var invocation = new CommandInvocation(
                    command, arguments,
                    params.getOrDefault("user_id", ""),
                    params.getOrDefault("channel_id", ""),
                    "slack",
                    Map.of("team_id",
                            params.getOrDefault("team_id", ""),
                           "team_domain",
                            params.getOrDefault("team_domain", ""),
                           "response_url", responseUrl));

            CommandResponse response = commandService.dispatch(
                    command, invocation);

            return switch (response) {
                case CommandResponse.Immediate imm -> {
                    String json = "{\"text\":\""
                            + escapeJson(imm.text()) + "\""
                            + (imm.ephemeral()
                            ? ",\"response_type\":\"ephemeral\""
                            : ",\"response_type\":\"in_channel\"")
                            + "}";
                    yield Response.ok(json).build();
                }
                case CommandResponse.Deferred def -> {
                    if (executor != null && !responseUrl.isBlank()) {
                        executor.submit(() -> {
                            DeferredReply reply = (txt, eph) ->
                                    postToResponseUrl(
                                            responseUrl, txt, eph);
                            def.callback().accept(reply);
                        });
                    }
                    yield Response.ok().build();
                }
            };
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to process Slack command", e);
            return Response.serverError().build();
        }
    }

    private boolean verifyTimestamp(String timestamp) {
        if (timestamp == null) return false;
        try {
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis() / 1000;
            return Math.abs(now - ts) <= MAX_TIMESTAMP_AGE_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean verifySignature(
            String signature, String timestamp, String body) {
        if (signature == null) return false;
        try {
            String basestring = "v0:" + timestamp + ":" + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    signingSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] hash = mac.doFinal(
                    basestring.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("v0=");
            for (byte b : hash)
                sb.append(String.format("%02x", b));
            String expected = sb.toString();
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> parseFormBody(String body) {
        Map<String, String> params = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1
                    ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                    : "";
            params.put(key, value);
        }
        return params;
    }

    private void postToResponseUrl(
            String url, String text, boolean ephemeral) {
        try {
            String json = "{\"text\":\"" + escapeJson(text) + "\""
                    + ",\"response_type\":\""
                    + (ephemeral ? "ephemeral" : "in_channel")
                    + "\"}";
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HTTP_CLIENT.send(request,
                            HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to send deferred Slack response", e);
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
