package io.casehub.connectors.web.model;

import java.util.List;
import java.util.Map;

public record FetchResult(String html, int statusCode, String contentType,
                          Map<String, List<String>> headers) {
}
