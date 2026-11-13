package io.casehub.connectors.document.microsoft;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.microsoft.GraphErrorHandler;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

class MicrosoftDriveClient {

    static final int SIMPLE_UPLOAD_LIMIT = 4 * 1024 * 1024;
    private static final int CHUNK_SIZE = 16 * 320 * 1024;
    private static final Set<String> ALLOWED_CDN_SUFFIXES = Set.of(
            ".sharepoint.com", ".1drv.com", ".blob.core.windows.net");

    private final MicrosoftGraphClient graph;
    private final String basePath;

    MicrosoftDriveClient(MicrosoftGraphClient graph, String basePath) {
        this.graph = graph;
        this.basePath = basePath;
    }

    Page<JsonObject> listChildren(String folderId, PageRequest pagination, String token) {
        String path = basePath + "/items/" + folderId + "/children?$top=" + pagination.pageSize();
        var page = graph.getPage(path, token, Function.identity(), pagination.cursor());
        var files = page.items().stream()
                .filter(item -> item.containsKey("file"))
                .toList();
        return new Page<>(files, page.nextCursor(), page.hasMore());
    }

    JsonObject getItem(String fileId, String token) {
        return graph.get(basePath + "/items/" + fileId, token);
    }

    byte[] downloadContent(String fileId, String token) {
        var response = graph.getRaw(basePath + "/items/" + fileId + "/content", token);
        if (response.statusCode() == 302) {
            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("302 without Location header"));
            validateCdnHost(location);
            return graph.getBytesFromUrl(location);
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        }
        throw new RuntimeException("Download failed: " + response.statusCode());
    }

    JsonObject upload(String folderId, String name, String contentType,
            byte[] content, String token) {
        if (content.length <= SIMPLE_UPLOAD_LIMIT) {
            return uploadSimple(folderId, name, contentType, content, token);
        }
        return uploadResumable(folderId, name, contentType, content, token);
    }

    void deleteItem(String fileId, String token) {
        graph.delete(basePath + "/items/" + fileId, token);
    }

    DeltaResult deltaSync(String deltaLink, String token) {
        String path = basePath + "/root/delta";
        List<JsonObject> changed = new ArrayList<>();
        List<String> deletedIds = new ArrayList<>();
        String cursor = deltaLink;

        do {
            var page = graph.getPage(path, token, Function.identity(), cursor);
            for (JsonObject item : page.items()) {
                if (item.containsKey("@removed")) {
                    deletedIds.add(item.getString("id"));
                } else if (item.containsKey("file")) {
                    changed.add(item);
                }
            }

            if (page.hasMore()) {
                cursor = page.nextCursor();
            } else {
                return new DeltaResult(List.copyOf(changed), List.copyOf(deletedIds),
                        page.nextCursor());
            }
        } while (true);
    }

    List<JsonObject> listFolders(String parentId, String token) {
        List<JsonObject> all = new ArrayList<>();
        String cursor = null;
        do {
            var page = graph.getPage(basePath + "/items/" + parentId + "/children",
                    token, Function.identity(), cursor);
            page.items().stream()
                    .filter(item -> item.containsKey("folder"))
                    .forEach(all::add);
            cursor = page.hasMore() ? page.nextCursor() : null;
        } while (cursor != null);
        return List.copyOf(all);
    }

    JsonObject createFolder(String parentId, String name, String token) {
        var body = Json.createObjectBuilder()
                .add("name", name)
                .add("folder", Json.createObjectBuilder())
                .add("@microsoft.graph.conflictBehavior", "fail")
                .build();
        return graph.post(basePath + "/items/" + parentId + "/children", body, token);
    }

    void moveItem(String fileId, String targetFolderId, String token) {
        var body = Json.createObjectBuilder()
                .add("parentReference", Json.createObjectBuilder()
                        .add("id", targetFolderId))
                .build();
        graph.patch(basePath + "/items/" + fileId, body, token);
    }

    Page<JsonObject> search(String query, PageRequest pagination, String token) {
        String escaped = query.replace("'", "''");
        String path = basePath + "/root/search(q='" + escaped + "')";
        var page = graph.getPage(path, token, Function.identity(), pagination.cursor());
        var files = page.items().stream()
                .filter(item -> item.containsKey("file"))
                .toList();
        return new Page<>(files, page.nextCursor(), page.hasMore());
    }

    String createShareLink(String fileId, String token) {
        var body = Json.createObjectBuilder()
                .add("type", "view")
                .add("scope", "anonymous")
                .build();
        var result = graph.post(basePath + "/items/" + fileId + "/createLink", body, token);
        return result.getJsonObject("link").getString("webUrl");
    }

    private JsonObject uploadSimple(String folderId, String name, String contentType,
            byte[] content, String token) {
        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        var request = HttpRequest.newBuilder(
                        URI.create(MicrosoftGraphClient.BASE_URL + basePath
                                + "/items/" + folderId + ":/" + encodedName + ":/content"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        var response = graph.send(request);
        GraphErrorHandler.handle(response, "microsoft");
        return parseJson(response.body());
    }

    private JsonObject uploadResumable(String folderId, String name, String contentType,
            byte[] content, String token) {
        var sessionBody = Json.createObjectBuilder()
                .add("item", Json.createObjectBuilder()
                        .add("@microsoft.graph.conflictBehavior", "replace")
                        .add("name", name))
                .build();
        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        var session = graph.post(basePath + "/items/" + folderId + ":/" + encodedName
                + ":/createUploadSession", sessionBody, token);
        String uploadUrl = session.getString("uploadUrl");

        int totalSize = content.length;
        int offset = 0;
        JsonObject result = null;
        while (offset < totalSize) {
            int end = Math.min(offset + CHUNK_SIZE, totalSize);
            byte[] chunk = Arrays.copyOfRange(content, offset, end);
            var request = HttpRequest.newBuilder(URI.create(uploadUrl))
                    .header("Content-Type", contentType)
                    .header("Content-Range",
                            "bytes " + offset + "-" + (end - 1) + "/" + totalSize)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(chunk))
                    .build();
            var response = graph.send(request);
            if (response.statusCode() >= 300) {
                GraphErrorHandler.handle(response, "microsoft");
            }
            if (end >= totalSize) {
                result = parseJson(response.body());
            }
            offset = end;
        }
        return result;
    }

    static void validateCdnHost(String url) {
        String host = URI.create(url).getHost();
        if (host == null) {
            throw new IllegalStateException("Blocked redirect to URL with no host: " + url);
        }
        host = host.toLowerCase();
        boolean allowed = ALLOWED_CDN_SUFFIXES.stream().anyMatch(host::endsWith);
        if (!allowed) {
            throw new IllegalStateException("Blocked redirect to untrusted host: " + host);
        }
    }

    private static JsonObject parseJson(String body) {
        try (var reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        }
    }

    record DeltaResult(List<JsonObject> changed, List<String> deletedIds, String deltaLink) {}
}
