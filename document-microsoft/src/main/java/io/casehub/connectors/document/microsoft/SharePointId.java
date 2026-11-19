package io.casehub.connectors.document.microsoft;

final class SharePointId {

    private static final String PREFIX = "sp:";

    private SharePointId() {}

    static String compose(String siteId, String itemId) {
        if (siteId == null) return itemId;
        return PREFIX + siteId + ":" + itemId;
    }

    static ParsedId parse(String id) {
        if (id == null) return new ParsedId(null, null);
        if (!id.startsWith(PREFIX)) return new ParsedId(null, id);
        int secondColon = id.indexOf(':', PREFIX.length());
        if (secondColon < 0) {
            throw new IllegalArgumentException(
                    "Expected sp:<siteId>:<itemId> format: " + id);
        }
        return new ParsedId(
                id.substring(PREFIX.length(), secondColon),
                id.substring(secondColon + 1));
    }

    record ParsedId(String siteId, String itemId) {}
}
