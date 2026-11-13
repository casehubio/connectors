package io.casehub.connectors.mobile.dial;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.ProviderErrorMapping;
import io.casehub.connectors.http.HttpHelper;
import io.casehub.connectors.mobile.dial.model.DialCall;
import io.casehub.connectors.mobile.dial.model.DialLookupResult;
import io.casehub.connectors.mobile.dial.model.DialMessage;
import io.casehub.connectors.mobile.dial.model.DialNumber;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

public class DialClient {

    private static final Logger LOG = Logger.getLogger(DialClient.class.getName());
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String PROVIDER = "dial";

    private final String baseUrl;
    private final String apiKey;

    public DialClient(String apiKey) {
        this(apiKey, "https://api.getdial.ai/v1");
    }

    DialClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public DialMessage sendMessage(String to, String body, String channel, String fromNumberId) {
        var json = "{\"to\":" + q(to)
                + ",\"body\":" + q(body)
                + (channel != null ? ",\"channel\":" + q(channel) : "")
                + (fromNumberId != null ? ",\"fromNumberId\":" + q(fromNumberId) : "")
                + "}";
        return post("/messages", json, DialMessage.class);
    }

    public List<DialMessage> listMessages() {
        return getList("/messages", new TypeReference<>() {});
    }

    public DialCall createCall(String to, String instruction, String fromNumberId,
                               String language, Integer maxDuration) {
        var sb = new StringBuilder("{\"to\":" + q(to));
        sb.append(",\"outboundInstruction\":").append(q(instruction));
        if (fromNumberId != null) sb.append(",\"fromNumberId\":").append(q(fromNumberId));
        if (language != null) sb.append(",\"language\":").append(q(language));
        if (maxDuration != null) sb.append(",\"maxDurationSeconds\":").append(maxDuration);
        sb.append('}');
        return post("/calls", sb.toString(), DialCall.class);
    }

    public DialCall getCall(String callId) {
        return get("/calls/" + URLEncoder.encode(callId, StandardCharsets.UTF_8), DialCall.class);
    }

    public List<DialCall> listCalls() {
        return getList("/calls", new TypeReference<>() {});
    }

    public void stopCall(String callId) {
        post("/calls/" + URLEncoder.encode(callId, StandardCharsets.UTF_8) + "/stop", "{}", Void.class);
    }

    public List<DialNumber> listNumbers() {
        return getList("/numbers", new TypeReference<>() {});
    }

    public DialLookupResult lookupNumber(String number) {
        return get("/lookup/number?number=" + URLEncoder.encode(number, StandardCharsets.UTF_8), DialLookupResult.class);
    }

    private <T> T post(String path, String json, Class<T> type) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            checkStatus(path, response);

            if (type == Void.class || response.body() == null || response.body().isBlank()) {
                return null;
            }
            return MAPPER.readValue(response.body(), type);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Dial API POST failed: " + path + " — " + e.getMessage(), e);
        }
    }

    private <T> T get(String path, Class<T> type) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            checkStatus(path, response);
            return MAPPER.readValue(response.body(), type);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Dial API GET failed: " + path + " — " + e.getMessage(), e);
        }
    }

    private <T> List<T> getList(String path, TypeReference<List<T>> typeRef) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            checkStatus(path, response);
            return MAPPER.readValue(response.body(), typeRef);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Dial API GET failed: " + path + " — " + e.getMessage(), e);
        }
    }

    private void checkStatus(String path, HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) return;

        String errorReason = null;
        try {
            var node = MAPPER.readTree(response.body());
            if (node.has("error")) errorReason = node.path("error").asText(null);
        } catch (Exception ignored) {}

        throw ProviderErrorMapping.mapStatusCode(status, response.body(), PROVIDER, errorReason);
    }

    static String q(String value) {
        return HttpHelper.jsonQuote(value);
    }

    static DialCall mapDialCall(JsonNode node) {
        return MAPPER.convertValue(node, DialCall.class);
    }

    static DialMessage mapDialMessage(JsonNode node) {
        return MAPPER.convertValue(node, DialMessage.class);
    }

    static DialNumber mapDialNumber(JsonNode node) {
        return MAPPER.convertValue(node, DialNumber.class);
    }

    static DialLookupResult mapDialLookup(JsonNode node) {
        return MAPPER.convertValue(node, DialLookupResult.class);
    }
}
