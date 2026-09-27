package io.casehub.connectors.chat.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.connectors.chat.command.CommandInvocation;
import io.casehub.connectors.chat.command.CommandResponse;
import io.casehub.connectors.chat.command.CommandService;
import io.casehub.connectors.chat.command.DeferredReply;
import io.casehub.connectors.discord.DiscordClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/interactions/discord")
@ApplicationScoped
public class DiscordInteractionEndpoint {

    private static final Logger LOG =
            Logger.getLogger(DiscordInteractionEndpoint.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CommandService commandService;
    private final DiscordClient client;
    private final String applicationId;
    private final PublicKey publicKey;
    private final ExecutorService executor;

    @Inject
    DiscordInteractionEndpoint(
            CommandService commandService,
            DiscordClient client,
            @ConfigProperty(name = "casehub.discord.application-id",
                    defaultValue = "") String applicationId,
            @ConfigProperty(name = "casehub.discord.public-key",
                    defaultValue = "") String publicKeyHex,
            org.eclipse.microprofile.context.ManagedExecutor executor) {
        this(commandService, client, applicationId,
                parsePublicKey(publicKeyHex), executor);
    }

    DiscordInteractionEndpoint(
            CommandService commandService,
            DiscordClient client,
            String applicationId,
            PublicKey publicKey,
            ExecutorService executor) {
        this.commandService = commandService;
        this.client = client;
        this.applicationId = applicationId;
        this.publicKey = publicKey;
        this.executor = executor;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleInteraction(
            @HeaderParam("X-Signature-Ed25519") String signature,
            @HeaderParam("X-Signature-Timestamp") String timestamp,
            String body) {

        if (publicKey == null) {
            return Response.status(503).build();
        }

        if (!verifySignature(signature, timestamp, body)) {
            return Response.status(401).build();
        }

        try {
            JsonNode json = MAPPER.readTree(body);
            int type = json.get("type").asInt();

            if (type == 1) {
                return Response.ok("{\"type\":1}").build();
            }

            if (type == 2) {
                return handleApplicationCommand(json);
            }

            return Response.status(400).build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to process interaction", e);
            return Response.serverError().build();
        }
    }

    private Response handleApplicationCommand(JsonNode json) {
        var data = json.get("data");
        String commandName = data.get("name").asText();

        Map<String, String> arguments = new HashMap<>();
        if (data.has("options")) {
            for (JsonNode opt : data.get("options")) {
                arguments.put(opt.get("name").asText(),
                        opt.get("value").asText());
            }
        }

        String userId = json.at("/member/user/id").asText(
                json.path("user").path("id").asText("unknown"));
        String channelId = json.path("channel_id").asText("");
        String interactionId = json.get("id").asText();
        String interactionToken = json.get("token").asText();

        Map<String, String> metadata = Map.of(
                "interaction_id", interactionId,
                "interaction_token", interactionToken,
                "guild_id", json.path("guild_id").asText(""));

        var invocation = new CommandInvocation(
                commandName, arguments, userId, channelId,
                "discord", metadata);

        CommandResponse response = commandService.dispatch(
                commandName, invocation);

        return switch (response) {
            case CommandResponse.Immediate imm -> {
                var respNode = MAPPER.createObjectNode();
                respNode.put("type", 4);
                var dataNode = respNode.putObject("data");
                dataNode.put("content", imm.text());
                if (imm.ephemeral()) dataNode.put("flags", 64);
                yield Response.ok(respNode.toString()).build();
            }
            case CommandResponse.Deferred def -> {
                if (executor != null) {
                    executor.submit(() -> {
                        DeferredReply reply = (text, ephemeral) ->
                                client.sendFollowupMessage(
                                        applicationId,
                                        interactionToken,
                                        text, ephemeral);
                        def.callback().accept(reply);
                    });
                }
                var respNode = MAPPER.createObjectNode();
                respNode.put("type", 5);
                if (def.ephemeral()) {
                    respNode.putObject("data").put("flags", 64);
                }
                yield Response.ok(respNode.toString()).build();
            }
        };
    }

    private boolean verifySignature(
            String signature, String timestamp, String body) {
        if (signature == null || timestamp == null) return false;
        try {
            byte[] sigBytes = HexFormat.of().parseHex(signature);
            byte[] message = (timestamp + body)
                    .getBytes(StandardCharsets.UTF_8);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(message);
            return verifier.verify(sigBytes);
        } catch (Exception e) {
            return false;
        }
    }

    static PublicKey parsePublicKey(String hex) {
        if (hex == null || hex.isBlank()) return null;
        try {
            byte[] keyBytes = HexFormat.of().parseHex(hex);
            KeyFactory kf = KeyFactory.getInstance("EdDSA");
            boolean xOdd = (keyBytes[keyBytes.length - 1] & 0x80) != 0;
            keyBytes[keyBytes.length - 1] &= 0x7f;
            byte[] reversed = new byte[keyBytes.length];
            for (int i = 0; i < keyBytes.length; i++) {
                reversed[i] = keyBytes[keyBytes.length - 1 - i];
            }
            var point = new EdECPoint(xOdd,
                    new BigInteger(1, reversed));
            return kf.generatePublic(new EdECPublicKeySpec(
                    NamedParameterSpec.ED25519, point));
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to parse Ed25519 public key", e);
            return null;
        }
    }
}
