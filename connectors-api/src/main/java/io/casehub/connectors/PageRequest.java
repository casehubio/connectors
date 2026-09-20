package io.casehub.connectors;

public record PageRequest(String cursor, int pageSize) {

    public static PageRequest first(int pageSize) {
        return new PageRequest(null, pageSize);
    }
}
