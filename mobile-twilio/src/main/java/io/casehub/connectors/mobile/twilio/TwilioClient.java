package io.casehub.connectors.mobile.twilio;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.ProviderErrorMapping;
import io.casehub.connectors.http.HttpHelper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.logging.Logger;

public class TwilioClient {

    private static final Logger LOG = Logger.getLogger(TwilioClient.class.getName());
    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String PROVIDER = "twilio";

    private final String accountSid;
    private final String authToken;
    private final String baseUrl;
    private final String credentials;

    public TwilioClient(String accountSid, String authToken) {
        this(accountSid, authToken, "https://api.twilio.com/2010-04-01");
    }

    TwilioClient(String accountSid, String authToken, String baseUrl) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.baseUrl = baseUrl;
        this.credentials = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
    }

    public JsonNode sendMessage(String to, String from, String body) {
        var formBody = "To=" + enc(to) + "&From=" + enc(from) + "&Body=" + enc(body);
        return postForm("/Accounts/" + accountSid + "/Messages.json", formBody);
    }

    public JsonNode sendWhatsAppMessage(String to, String from, String body) {
        var formBody = "To=" + enc("whatsapp:" + to) + "&From=" + enc("whatsapp:" + from)
                + "&Body=" + enc(body);
        return postForm("/Accounts/" + accountSid + "/Messages.json", formBody);
    }

    public JsonNode listMessages() {
        return get("/Accounts/" + accountSid + "/Messages.json");
    }

    public JsonNode createCall(String to, String from, String twiml) {
        var formBody = "To=" + enc(to) + "&From=" + enc(from) + "&Twiml=" + enc(twiml);
        return postForm("/Accounts/" + accountSid + "/Calls.json", formBody);
    }

    public JsonNode getCall(String callSid) {
        return get("/Accounts/" + accountSid + "/Calls/" + callSid + ".json");
    }

    public JsonNode listCalls() {
        return get("/Accounts/" + accountSid + "/Calls.json");
    }

    public JsonNode updateCall(String callSid, String status) {
        var formBody = "Status=" + enc(status);
        return postForm("/Accounts/" + accountSid + "/Calls/" + callSid + ".json", formBody);
    }

    public JsonNode listRecordings(String callSid) {
        return get("/Accounts/" + accountSid + "/Calls/" + callSid + "/Recordings.json");
    }

    public JsonNode listIncomingPhoneNumbers() {
        return get("/Accounts/" + accountSid + "/IncomingPhoneNumbers.json");
    }

    public JsonNode lookupNumber(String phoneNumber) {
        return get("https://lookups.twilio.com/v1/PhoneNumbers/" + enc(phoneNumber)
                + "?Type=carrier");
    }

    private JsonNode postForm(String path, String formBody) {
        try {
            var uri = path.startsWith("http") ? URI.create(path) : URI.create(baseUrl + path);
            var request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "Basic " + credentials)
                    .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                    .build();

            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            checkStatus(path, response);
            return MAPPER.readTree(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Twilio API POST failed: " + path + " — " + e.getMessage(), e);
        }
    }

    private JsonNode get(String path) {
        try {
            var uri = path.startsWith("http") ? URI.create(path) : URI.create(baseUrl + path);
            var request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + credentials)
                    .GET()
                    .build();

            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            checkStatus(path, response);
            return MAPPER.readTree(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Twilio API GET failed: " + path + " — " + e.getMessage(), e);
        }
    }

    private void checkStatus(String path, HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) return;

        String errorReason = null;
        try {
            var node = MAPPER.readTree(response.body());
            if (node.has("message")) errorReason = node.path("message").asText(null);
        } catch (Exception ignored) {}

        throw ProviderErrorMapping.mapStatusCode(status, response.body(), PROVIDER, errorReason);
    }

    String authToken() {
        return authToken;
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
