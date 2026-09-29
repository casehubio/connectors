package io.casehub.connectors;

public record SyncRequest(String syncToken, int pageSize) {

    public static SyncRequest initial(int pageSize) {
        return new SyncRequest(null, pageSize);
    }
}
