package io.casehub.connectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderExceptionTest {

    @Test
    void providerApiException_carriesFields() {
        var ex = new ProviderApiException(500, "Internal Server Error", "google");
        assertThat(ex.statusCode()).isEqualTo(500);
        assertThat(ex.responseBody()).isEqualTo("Internal Server Error");
        assertThat(ex.provider()).isEqualTo("google");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).contains("google").contains("500");
    }

    @Test
    void providerRateLimitedException_carriesRetryAfter() {
        var ex = new ProviderRateLimitedException(30, "google");
        assertThat(ex.retryAfterSeconds()).isEqualTo(30);
        assertThat(ex.statusCode()).isEqualTo(429);
        assertThat(ex.provider()).isEqualTo("google");
        assertThat(ex).isInstanceOf(ProviderApiException.class);
    }

    @Test
    void providerAuthException_tokenExpired() {
        var ex = new ProviderAuthException(AuthFailureReason.TOKEN_EXPIRED, "google");
        assertThat(ex.reason()).isEqualTo(AuthFailureReason.TOKEN_EXPIRED);
        assertThat(ex.statusCode()).isEqualTo(401);
        assertThat(ex.provider()).isEqualTo("google");
        assertThat(ex).isInstanceOf(ProviderApiException.class);
    }

    @Test
    void providerAuthException_insufficientScope() {
        var ex = new ProviderAuthException(AuthFailureReason.INSUFFICIENT_SCOPE, "google");
        assertThat(ex.reason()).isEqualTo(AuthFailureReason.INSUFFICIENT_SCOPE);
        assertThat(ex.statusCode()).isEqualTo(403);
    }

    @Test
    void providerAuthException_forbidden() {
        var ex = new ProviderAuthException(AuthFailureReason.FORBIDDEN, "google");
        assertThat(ex.reason()).isEqualTo(AuthFailureReason.FORBIDDEN);
        assertThat(ex.statusCode()).isEqualTo(403);
    }

    @Test
    void syncTokenExpiredException_extendsProviderApiException() {
        var ex = new SyncTokenExpiredException("token-123", "google");
        assertThat(ex).isInstanceOf(ProviderApiException.class);
        assertThat(ex.expiredToken()).isEqualTo("token-123");
        assertThat(ex.statusCode()).isEqualTo(410);
        assertThat(ex.provider()).isEqualTo("google");
    }

    @Test
    void errorMapping_401_tokenExpired() {
        var ex = ProviderErrorMapping.mapStatusCode(401, "Unauthorized", "google", null);
        assertThat(ex).isInstanceOf(ProviderAuthException.class);
        assertThat(((ProviderAuthException) ex).reason()).isEqualTo(AuthFailureReason.TOKEN_EXPIRED);
    }

    @Test
    void errorMapping_403_insufficientPermissions() {
        var ex = ProviderErrorMapping.mapStatusCode(403, "Forbidden", "google", "insufficientPermissions");
        assertThat(ex).isInstanceOf(ProviderAuthException.class);
        assertThat(((ProviderAuthException) ex).reason()).isEqualTo(AuthFailureReason.INSUFFICIENT_SCOPE);
    }

    @Test
    void errorMapping_403_otherReason_forbidden() {
        var ex = ProviderErrorMapping.mapStatusCode(403, "Forbidden", "google", "domainPolicy");
        assertThat(ex).isInstanceOf(ProviderAuthException.class);
        assertThat(((ProviderAuthException) ex).reason()).isEqualTo(AuthFailureReason.FORBIDDEN);
    }

    @Test
    void errorMapping_403_nullReason_forbidden() {
        var ex = ProviderErrorMapping.mapStatusCode(403, "Forbidden", "google", null);
        assertThat(ex).isInstanceOf(ProviderAuthException.class);
        assertThat(((ProviderAuthException) ex).reason()).isEqualTo(AuthFailureReason.FORBIDDEN);
    }

    @Test
    void errorMapping_404_noSuchElement() {
        var ex = ProviderErrorMapping.mapStatusCode(404, "Not Found", "google", null);
        assertThat(ex).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void errorMapping_410_syncTokenExpired() {
        var ex = ProviderErrorMapping.mapStatusCode(410, "expired-token", "google", null);
        assertThat(ex).isInstanceOf(SyncTokenExpiredException.class);
        assertThat(((SyncTokenExpiredException) ex).expiredToken()).isEqualTo("expired-token");
    }

    @Test
    void errorMapping_429_rateLimited() {
        var ex = ProviderErrorMapping.mapStatusCode(429, "Too Many Requests", "google", null);
        assertThat(ex).isInstanceOf(ProviderRateLimitedException.class);
        assertThat(((ProviderRateLimitedException) ex).retryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void errorMapping_500_genericProviderError() {
        var ex = ProviderErrorMapping.mapStatusCode(500, "Server Error", "google", null);
        assertThat(ex).isInstanceOf(ProviderApiException.class);
        assertThat(((ProviderApiException) ex).statusCode()).isEqualTo(500);
    }
}
