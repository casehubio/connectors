package io.casehub.connectors.mobile.dial;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.connectors.http.HttpHelper;
import io.casehub.connectors.mobile.model.Call;
import io.casehub.connectors.mobile.model.CallOptions;
import io.casehub.connectors.mobile.model.CallStatus;
import io.casehub.connectors.mobile.model.ConversationAction;
import io.casehub.connectors.mobile.model.ConversationHandler;
import io.casehub.connectors.mobile.model.ConversationResponse;
import io.casehub.connectors.mobile.model.Speaker;
import io.casehub.connectors.mobile.model.TranscriptTurn;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

class DialWebSocket {

    private static final Logger LOG = Logger.getLogger(DialWebSocket.class.getName());
    private static final String ERROR_MESSAGE = "I'm sorry, I'm having technical difficulties. Goodbye.";

    private final String apiKey;
    private final String baseWsUrl;

    DialWebSocket(String apiKey) {
        this(apiKey, "wss://api.getdial.ai/v1/ws");
    }

    DialWebSocket(String apiKey, String baseWsUrl) {
        this.apiKey = apiKey;
        this.baseWsUrl = baseWsUrl;
    }

    Call initiateCall(String to, ConversationHandler handler, CallOptions options) {
        var callId = "call_ws_" + System.nanoTime();
        var from = options.fromNumberId();
        var startedAt = Instant.now();
        var turns = new ArrayList<TranscriptTurn>();
        var latch = new CountDownLatch(1);
        var failureRef = new String[]{null};
        var statusRef = new CallStatus[]{CallStatus.QUEUED};

        try {
            handler.onCallConnected();
            statusRef[0] = CallStatus.IN_PROGRESS;

            var hmac = HttpHelper.hmacSha256Hex(callId, apiKey);
            var wsUri = URI.create(baseWsUrl + "/calls/" + callId + "?auth=" + hmac);

            var listener = new WebSocket.Listener() {
                private final StringBuilder buffer = new StringBuilder();

                @Override
                public void onOpen(WebSocket ws) {
                    var initJson = "{\"type\":\"init\",\"to\":" + DialClient.q(to)
                            + (options.language() != null ? ",\"language\":" + DialClient.q(options.language()) : "")
                            + (options.maxDurationSeconds() != null ? ",\"maxDurationSeconds\":" + options.maxDurationSeconds() : "")
                            + "}";
                    ws.sendText(initJson, true);
                    ws.request(1);
                }

                @Override
                public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                    buffer.append(data);
                    if (!last) {
                        ws.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    var text = buffer.toString();
                    buffer.setLength(0);

                    try {
                        var node = DialClient.MAPPER.readTree(text);
                        var type = node.path("type").asText();

                        switch (type) {
                            case "response_required" -> handleResponseRequired(ws, node, handler, turns);
                            case "call_ended" -> {
                                statusRef[0] = CallStatus.COMPLETED;
                                latch.countDown();
                            }
                            case "error" -> {
                                failureRef[0] = node.path("message").asText("Unknown error");
                                statusRef[0] = CallStatus.FAILED;
                                latch.countDown();
                            }
                            default -> LOG.fine("Unknown Dial WS message type: " + type);
                        }
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error processing Dial WS message", e);
                        failureRef[0] = e.getMessage();
                        statusRef[0] = CallStatus.FAILED;
                        latch.countDown();
                    }

                    ws.request(1);
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                    if (statusRef[0] == CallStatus.IN_PROGRESS) {
                        statusRef[0] = CallStatus.COMPLETED;
                    }
                    latch.countDown();
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public void onError(WebSocket ws, Throwable error) {
                    LOG.log(Level.WARNING, "Dial WebSocket error", error);
                    failureRef[0] = error.getMessage();
                    statusRef[0] = CallStatus.FAILED;
                    latch.countDown();
                }
            };

            HttpHelper.CLIENT.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(wsUri, listener)
                    .join();

            int maxSeconds = options.maxDurationSeconds() != null ? options.maxDurationSeconds() : 300;
            latch.await(maxSeconds, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failureRef[0] = "Call interrupted";
            statusRef[0] = CallStatus.CANCELLED;
        } catch (Exception e) {
            failureRef[0] = e.getMessage();
            statusRef[0] = CallStatus.FAILED;
        }

        var endedAt = Instant.now();
        var duration = Duration.between(startedAt, endedAt);
        var transcript = buildTranscript(turns);
        var call = new Call(callId, to, from, statusRef[0], transcript, List.copyOf(turns),
                duration, startedAt, endedAt, failureRef[0]);

        handler.onCallEnded(call);
        return call;
    }

    private void handleResponseRequired(WebSocket ws, JsonNode node,
                                         ConversationHandler handler, List<TranscriptTurn> turns) {
        var humanText = node.path("text").asText("");
        var startMs = node.path("startMs").asLong(0);
        var endMs = node.path("endMs").asLong(0);

        turns.add(new TranscriptTurn(Speaker.HUMAN, humanText, startMs, endMs));

        try {
            var response = handler.onHumanSpeech(humanText, List.copyOf(turns));
            var agentStartMs = System.currentTimeMillis();

            if (response.text() != null && !response.text().isBlank()) {
                turns.add(new TranscriptTurn(Speaker.AGENT, response.text(), agentStartMs,
                        System.currentTimeMillis()));
            }

            var responseJson = buildResponseJson(response);
            ws.sendText(responseJson, true);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "ConversationHandler threw during onHumanSpeech", e);
            turns.add(new TranscriptTurn(Speaker.AGENT, ERROR_MESSAGE,
                    System.currentTimeMillis(), System.currentTimeMillis()));
            var errorResponse = "{\"type\":\"response\",\"text\":" + DialClient.q(ERROR_MESSAGE)
                    + ",\"action\":\"hangup\"}";
            ws.sendText(errorResponse, true);
        }
    }

    private String buildResponseJson(ConversationResponse response) {
        var sb = new StringBuilder("{\"type\":\"response\"");
        if (response.text() != null) {
            sb.append(",\"text\":").append(DialClient.q(response.text()));
        }
        if (response.action() != null) {
            switch (response.action()) {
                case ConversationAction.Hold h -> sb.append(",\"action\":\"hold\"");
                case ConversationAction.Transfer t ->
                        sb.append(",\"action\":\"transfer\",\"transferTo\":").append(DialClient.q(t.transferTo()));
                case ConversationAction.HangUp h -> sb.append(",\"action\":\"hangup\"");
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private String buildTranscript(List<TranscriptTurn> turns) {
        var sb = new StringBuilder();
        for (var turn : turns) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(turn.speaker() == Speaker.AGENT ? "Agent" : "Human");
            sb.append(": ").append(turn.text());
        }
        return sb.toString();
    }
}
