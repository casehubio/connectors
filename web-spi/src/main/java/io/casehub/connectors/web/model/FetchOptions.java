package io.casehub.connectors.web.model;

import java.time.Duration;
import java.util.Map;

public record FetchOptions(Map<String, String> cookies, Map<String, String> headers,
                           Duration timeout) {

    public static FetchOptions withCookies(Map<String, String> cookies) {
        return new FetchOptions(cookies, Map.of(), null);
    }

    public static FetchOptions withHeaders(Map<String, String> headers) {
        return new FetchOptions(Map.of(), headers, null);
    }

    public static FetchOptions withTimeout(Duration timeout) {
        return new FetchOptions(Map.of(), Map.of(), timeout);
    }
}
