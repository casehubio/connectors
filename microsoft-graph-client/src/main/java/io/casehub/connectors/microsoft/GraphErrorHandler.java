package io.casehub.connectors.microsoft;

import io.casehub.connectors.AuthFailureReason;
import io.casehub.connectors.ProviderAuthException;
import io.casehub.connectors.ProviderErrorMapping;
import io.casehub.connectors.ProviderRateLimitedException;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.util.Set;

public final class GraphErrorHandler {

    private static final int DEFAULT_RETRY_AFTER = 30;
    private static final Set<String> SCOPE_ERROR_CODES = Set.of(
            "Authorization_RequestDenied", "ErrorAccessDenied", "AccessDenied");

    private GraphErrorHandler() {}

    public static void handle(HttpResponse<?> response, String provider) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) return;

        String body = bodyAsString(response);
        String errorCode = extractErrorCode(body);

        if (status == 403) {
            var reason = SCOPE_ERROR_CODES.contains(errorCode)
                    ? AuthFailureReason.INSUFFICIENT_SCOPE
                    : AuthFailureReason.FORBIDDEN;
            throw new ProviderAuthException(reason, provider);
        }

        if (status == 429) {
            int retryAfter = parseRetryAfter(response, DEFAULT_RETRY_AFTER);
            throw new ProviderRateLimitedException(retryAfter, provider);
        }

        throw ProviderErrorMapping.mapStatusCode(status, body, provider, errorCode);
    }

    static String extractErrorCode(String body) {
        try (var reader = Json.createReader(new StringReader(body))) {
            JsonObject json = reader.readObject();
            if (json.containsKey("error")) {
                return json.getJsonObject("error").getString("code", "");
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private static int parseRetryAfter(HttpResponse<?> response, int defaultValue) {
        return response.headers().firstValue("Retry-After")
                .map(v -> {
                    try {
                        return Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    private static String bodyAsString(HttpResponse<?> response) {
        Object body = response.body();
        if (body instanceof String s) return s;
        if (body instanceof byte[] b) return new String(b);
        return "";
    }
}
