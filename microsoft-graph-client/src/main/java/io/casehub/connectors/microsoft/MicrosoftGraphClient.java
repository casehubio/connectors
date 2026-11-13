package io.casehub.connectors.microsoft;

import io.casehub.connectors.Page;
import io.casehub.connectors.http.HttpHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

@ApplicationScoped
public class MicrosoftGraphClient {

    static final String BASE_URL = "https://graph.microsoft.com/v1.0";
    private static final int MAX_PAGES = 50;
    private static final Logger LOG = Logger.getLogger(MicrosoftGraphClient.class.getName());

    @FunctionalInterface
    public interface HttpSender {
        HttpResponse<String> send(HttpRequest request) throws Exception;
    }

    private final HttpSender sender;

    public MicrosoftGraphClient() {
        this.sender = request -> HttpHelper.CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString());
    }

    public MicrosoftGraphClient(HttpSender sender) {
        this.sender = sender;
    }

    public JsonObject get(String path, String accessToken) {
        var request = jsonRequest(path, accessToken).GET().build();
        return sendAndParse(request);
    }

    public JsonObject post(String path, JsonObject body, String accessToken) {
        var request = jsonRequest(path, accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Content-Type", "application/json")
                .build();
        return sendAndParse(request);
    }

    public JsonObject patch(String path, JsonObject body, String accessToken) {
        var request = jsonRequest(path, accessToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Content-Type", "application/json")
                .build();
        return sendAndParse(request);
    }

    public void delete(String path, String accessToken) {
        var request = jsonRequest(path, accessToken).DELETE().build();
        var response = doSend(request);
        if (response.statusCode() >= 300) {
            GraphErrorHandler.handle(response, "microsoft");
        }
    }

    public byte[] getBytes(String path, String accessToken) {
        var request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        try {
            var response = HttpHelper.CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 300) {
                throw ProviderErrorFromBytes(response);
            }
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void putBytes(String path, byte[] body, String contentType,
            String accessToken) {
        var request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            var response = HttpHelper.CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                GraphErrorHandler.handle(response, "microsoft");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public HttpResponse<byte[]> getRaw(String path, String accessToken) {
        var request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        try {
            return HttpHelper.CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getBytesFromUrl(String absoluteUrl) {
        var request = HttpRequest.newBuilder(URI.create(absoluteUrl))
                .GET().build();
        try {
            var response = HttpHelper.CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("CDN fetch failed: " + response.statusCode());
            }
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> Page<T> getPage(String path, String accessToken,
            Function<JsonObject, T> mapper, String cursor) {
        URI uri = (cursor != null) ? URI.create(cursor) : toUri(BASE_URL + path);
        var request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET().build();

        var response = doSend(request);
        GraphErrorHandler.handle(response, "microsoft");

        var json = parseJson(response.body());
        JsonArray valueArray = json.getJsonArray("value");
        List<T> items = new ArrayList<>();
        if (valueArray != null) {
            for (var item : valueArray) {
                items.add(mapper.apply(item.asJsonObject()));
            }
        }
        String nextLink = json.containsKey("@odata.nextLink")
                ? json.getString("@odata.nextLink") : null;
        return new Page<>(List.copyOf(items), nextLink, nextLink != null);
    }

    private HttpRequest.Builder jsonRequest(String path, String accessToken) {
        return HttpRequest.newBuilder(toUri(BASE_URL + path))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json");
    }

    private HttpResponse<String> doSend(HttpRequest request) {
        try {
            return sender.send(request);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonObject sendAndParse(HttpRequest request) {
        var response = doSend(request);
        GraphErrorHandler.handle(response, "microsoft");
        String body = response.body();
        if (body == null || body.isBlank()) return JsonObject.EMPTY_JSON_OBJECT;
        return parseJson(body);
    }

    private static JsonObject parseJson(String body) {
        try (var reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        }
    }

    private static URI toUri(String rawUrl) {
        try {
            var parsed = new java.net.URL(rawUrl);
            return new URI(parsed.getProtocol(), parsed.getAuthority(),
                           parsed.getPath(), parsed.getQuery(), parsed.getRef());
        } catch (Exception e) {
            return URI.create(rawUrl);
        }
    }


    private static RuntimeException ProviderErrorFromBytes(HttpResponse<byte[]> response) {
        String body = new String(response.body());
        String errorCode = GraphErrorHandler.extractErrorCode(body);
        return io.casehub.connectors.ProviderErrorMapping.mapStatusCode(
                response.statusCode(), body, "microsoft", errorCode);
    }
}
