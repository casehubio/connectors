package io.casehub.connectors.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.http.HttpHelper;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

@Plugin(value = "http-post", description = "HTTP POST with JSON body — fails on non-2xx", absorbSiblings = true)
public record HttpPostPlugin(@Required String url, @Optional Object body) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Execute
    public Result execute() {
        try {
            var jsonBody = body != null ? MAPPER.writeValueAsString(body) : "{}";
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                var output = new LinkedHashMap<String, Object>();
                output.put("status", response.statusCode());
                output.put("body", response.body());
                return Result.of(output);
            }
            return Result.failed("HTTP POST " + url + " returned " + response.statusCode());
        } catch (JsonProcessingException e) {
            return Result.failed("Failed to serialize body to JSON: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed("HTTP POST interrupted: " + url);
        } catch (Exception e) {
            return Result.failed("HTTP POST failed: " + url + " — " + e.getMessage());
        }
    }
}
