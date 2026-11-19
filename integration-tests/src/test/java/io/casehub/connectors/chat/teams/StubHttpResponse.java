package io.casehub.connectors.chat.teams;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

final class StubHttpResponse implements HttpResponse<String> {

    private final int statusCode;
    private final String body;

    StubHttpResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    @Override public int statusCode() { return statusCode; }
    @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (k, v) -> true); }
    @Override public String body() { return body; }
    @Override public HttpRequest request() { return null; }
    @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
    @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
    @Override public URI uri() { return URI.create("https://graph.microsoft.com/v1.0/stub"); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
}
