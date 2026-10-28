package io.casehub.connectors;

import java.util.NoSuchElementException;

public final class ProviderErrorMapping {

    private static final int DEFAULT_RETRY_AFTER_SECONDS = 30;

    private ProviderErrorMapping() {}

    public static RuntimeException mapStatusCode(int statusCode, String body,
            String provider, String errorReason) {
        return switch (statusCode) {
            case 401 -> new ProviderAuthException(AuthFailureReason.TOKEN_EXPIRED, provider);
            case 403 -> {
                var reason = "insufficientPermissions".equals(errorReason)
                    ? AuthFailureReason.INSUFFICIENT_SCOPE
                    : AuthFailureReason.FORBIDDEN;
                yield new ProviderAuthException(reason, provider);
            }
            case 404 -> new NoSuchElementException(body);
            case 410 -> new SyncTokenExpiredException(body, provider);
            case 429 -> new ProviderRateLimitedException(DEFAULT_RETRY_AFTER_SECONDS, provider);
            default -> new ProviderApiException(statusCode, body, provider);
        };
    }
}
