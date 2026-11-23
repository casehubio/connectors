package io.casehub.connectors.mobile.numbrrs;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.http.HttpHelper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NumbrrsClient {

    private static final Logger LOG = Logger.getLogger(NumbrrsClient.class.getName());
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String apiKey;
    private final String baseUrl;

    public NumbrrsClient(String apiKey) {
        this(apiKey, "https://sms.numbrrs.com/api/v1");
    }

    NumbrrsClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public JsonNode listMessages() {
        return get("/messages");
    }

    private JsonNode get(String path) {
        try {
            var resp = HttpHelper.CLIENT.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + path))
                            .header("Authorization", "ApiKey " + apiKey)
                            .GET()
                            .timeout(TIMEOUT)
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                LOG.warning("numbrrs GET " + path + " failed: HTTP " + resp.statusCode());
                return null;
            }
            return MAPPER.readTree(resp.body());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "numbrrs GET " + path + " error", e);
            return null;
        }
    }

}
