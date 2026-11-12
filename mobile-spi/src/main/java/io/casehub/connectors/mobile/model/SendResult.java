package io.casehub.connectors.mobile.model;

public record SendResult(boolean ok, String messageId, String status, String error) {

    public static SendResult success(String messageId) {
        return new SendResult(true, messageId, "sent", null);
    }

    public static SendResult failure(String error) {
        return new SendResult(false, null, "failed", error);
    }
}
