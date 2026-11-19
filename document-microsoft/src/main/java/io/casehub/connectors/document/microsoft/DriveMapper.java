package io.casehub.connectors.document.microsoft;

import io.casehub.connectors.document.model.DocumentMetadata;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.model.Folder;

import jakarta.json.JsonObject;
import java.time.Instant;

final class DriveMapper {

    private DriveMapper() {}

    static DocumentSummary toSummary(JsonObject item) {
        return toSummary(item, null);
    }

    static DocumentSummary toSummary(JsonObject item, String siteId) {
        return new DocumentSummary(
                SharePointId.compose(siteId, item.getString("id")),
                item.getString("name"),
                SharePointId.compose(siteId, parentId(item)),
                mimeType(item),
                item.getJsonNumber("size").longValue(),
                parseInstant(item, "createdDateTime"),
                parseInstant(item, "lastModifiedDateTime"));
    }


    static DocumentMetadata toMetadata(JsonObject item) {
        return toMetadata(item, null);
    }

    static DocumentMetadata toMetadata(JsonObject item, String siteId) {
        String owner = null;
        if (item.containsKey("createdBy")) {
            var createdBy = item.getJsonObject("createdBy");
            if (createdBy.containsKey("user")) {
                owner = createdBy.getJsonObject("user").getString("displayName", null);
            }
        }
        return new DocumentMetadata(
                SharePointId.compose(siteId, item.getString("id")),
                item.getString("name"),
                SharePointId.compose(siteId, parentId(item)),
                mimeType(item),
                item.getJsonNumber("size").longValue(),
                owner,
                item.getString("webUrl", null),
                parseInstant(item, "createdDateTime"),
                parseInstant(item, "lastModifiedDateTime"));
    }


    static Folder toFolder(JsonObject item) {
        return toFolder(item, null);
    }

    static Folder toFolder(JsonObject item, String siteId) {
        return new Folder(
                SharePointId.compose(siteId, item.getString("id")),
                item.getString("name"),
                SharePointId.compose(siteId, parentId(item)),
                parseInstant(item, "createdDateTime"));
    }


    private static String parentId(JsonObject item) {
        if (item.containsKey("parentReference")) {
            var parent = item.getJsonObject("parentReference");
            return parent.getString("id", null);
        }
        return null;
    }

    private static String mimeType(JsonObject item) {
        if (item.containsKey("file")) {
            return item.getJsonObject("file").getString("mimeType", null);
        }
        return null;
    }

    private static Instant parseInstant(JsonObject item, String field) {
        if (!item.containsKey(field)) return null;
        return Instant.parse(item.getString(field));
    }
}
