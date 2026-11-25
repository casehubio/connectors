package io.casehub.connectors.steps;

import io.casehub.connectors.http.HttpHelper;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;

@Plugin(value = "health-check", description = "HTTP GET with retry — fails playbook if non-2xx after 3 attempts", absorbSiblings = true)
public record HealthCheckPlugin(@Required String url) {

    private static final int MAX_RETRIES = 3;
    private static final long BACKOFF_MS = 2000;

    @Execute
    public Result execute() {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                var request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    var output = new LinkedHashMap<String, Object>();
                    output.put("status", response.statusCode());
                    output.put("body", response.body());
                    return Result.of(output);
                }
                lastException = new RuntimeException("HTTP " + response.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.failed("Health check interrupted: " + url);
            } catch (Exception e) {
                lastException = e;
            }
            if (attempt < MAX_RETRIES) {
                try { Thread.sleep(BACKOFF_MS); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Result.failed("Health check interrupted: " + url);
                }
            }
        }
        return Result.failed("Health check failed after " + MAX_RETRIES + " attempts: " + url
                + " — " + (lastException != null ? lastException.getMessage() : "unknown error"));
    }
}
