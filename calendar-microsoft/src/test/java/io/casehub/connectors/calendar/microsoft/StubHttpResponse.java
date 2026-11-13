package io.casehub.connectors.calendar.microsoft;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class StubHttpResponse implements HttpResponse<String> {

    private final int statusCode;
    private final String body;
    private final HttpHeaders headers;

    StubHttpResponse(int statusCode, String body) {
        this(statusCode, body, Map.of());
    }

    StubHttpResponse(int statusCode, String body, Map<String, List<String>> headers) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = HttpHeaders.of(headers, (k, v) -> true);
    }

    @Override public int statusCode() { return statusCode; }
    @Override public HttpHeaders headers() { return headers; }
    @Override public String body() { return body; }
    @Override public HttpRequest request() { return null; }
    @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
    @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
    @Override public URI uri() { return URI.create("https://graph.microsoft.com/v1.0/stub"); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
}
