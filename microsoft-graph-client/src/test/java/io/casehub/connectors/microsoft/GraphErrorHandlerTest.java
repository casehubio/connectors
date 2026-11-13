package io.casehub.connectors.microsoft;

import io.casehub.connectors.AuthFailureReason;
import io.casehub.connectors.ProviderApiException;
import io.casehub.connectors.ProviderAuthException;
import io.casehub.connectors.ProviderRateLimitedException;
import io.casehub.connectors.SyncTokenExpiredException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.*;

class GraphErrorHandlerTest {

    @Test
    void doesNotThrowFor2xx() {
        var response = stub(200, "{}");
        assertThatCode(() -> GraphErrorHandler.handle(response, "microsoft"))
                .doesNotThrowAnyException();
    }

    @Test
    void maps401ToTokenExpired() {
        var response = stub(401, errorBody("InvalidAuthenticationToken", "expired"));
        assertThatThrownBy(() -> GraphErrorHandler.handle(response, "microsoft"))
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(e -> assertThat(((ProviderAuthException) e).reason())
                        .isEqualTo(AuthFailureReason.TOKEN_EXPIRED));
    }

    @Test
    void maps403AuthorizationRequestDeniedToInsufficientScope() {
        var response = stub(403, errorBody("Authorization_RequestDenied", "denied"));
        assertThatThrownBy(() -> GraphErrorHandler.handle(response, "microsoft"))
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(e -> assertThat(((ProviderAuthException) e).reason())
                        .isEqualTo(AuthFailureReason.INSUFFICIENT_SCOPE));
    }

    @Test
    void maps403ErrorAccessDeniedToInsufficientScope() {
        var response = stub(403, errorBody("ErrorAccessDenied", "denied"));
        assertThatThrownBy(() -> GraphErrorHandler.handle(response, "microsoft"))
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(e -> assertThat(((ProviderAuthException) e).reason())
                        .isEqualTo(AuthFailureReason.INSUFFICIENT_SCOPE));
    }

    @Test
    void maps403AccessDeniedToInsufficientScope() {
        var response = stub(403, errorBody("AccessDenied", "denied"));
        assertThatThrownBy(() -> GraphErrorHandler.handle(response, "microsoft"))
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(e -> assertThat(((ProviderAuthException) e).reason())
                        .isEqualTo(AuthFailureReason.INSUFFICIENT_SCOPE));
    }

    @Test
    void maps403UnknownCodeToForbidden() {
        var response = stub(403, errorBody("SomeOtherError", "denied"));
        assertThatThrownBy(() -> GraphErrorHandler.handle(response, "microsoft"))
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(e -> assertThat(((ProviderAuthException) e).reason())
                        .isEqualTo(AuthFailureReason.FORBIDDEN));
    }

    @Test
    void maps404ToNoSuchElement() {
        var response = stub(404, errorBody("ErrorItemNotFound", "not found"));
        assertThatThrownBy(() -> GraphErrorHandler.handle(response, "microsoft"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void maps410ToSyncTokenExpired() {
        var response = stub(410, errorBody("resyncRequired", "token expired"));
        assertThatThrownBy(() -> GraphErrorHandler.handle(response, "microsoft"))
                .isInstanceOf(SyncTokenExpiredException.class);
    }

    @Test
    void maps429WithRetryAfterHeader() {
        var response = new StubHttpResponse(429,
                errorBody("activityLimitReached", "throttled"),
                Map.of("Retry-After", List.of("45")));
        assertThatThrownBy(() -> GraphErrorHandler.handle(response, "microsoft"))
                .isInstanceOf(ProviderRateLimitedException.class)
                .satisfies(e -> assertThat(((ProviderRateLimitedException) e).retryAfterSeconds())
                        .isEqualTo(45));
    }

    @Test
    void maps429WithoutRetryAfterDefaultsTo30() {
        var response = stub(429, errorBody("activityLimitReached", "throttled"));
        assertThatThrownBy(() -> GraphErrorHandler.handle(response, "microsoft"))
                .isInstanceOf(ProviderRateLimitedException.class)
                .satisfies(e -> assertThat(((ProviderRateLimitedException) e).retryAfterSeconds())
                        .isEqualTo(30));
    }

    @Test
    void maps500ToProviderApiException() {
        var response = stub(500, errorBody("InternalServerError", "internal error"));
        assertThatThrownBy(() -> GraphErrorHandler.handle(response, "microsoft"))
                .isInstanceOf(ProviderApiException.class);
    }

    @Test
    void extractsErrorCodeFromGraphResponse() {
        String body = errorBody("TestCode", "test message");
        assertThat(GraphErrorHandler.extractErrorCode(body)).isEqualTo("TestCode");
    }

    @Test
    void extractsEmptyCodeFromMalformedJson() {
        assertThat(GraphErrorHandler.extractErrorCode("not json")).isEmpty();
    }

    private static StubHttpResponse stub(int status, String body) {
        return new StubHttpResponse(status, body);
    }

    private static String errorBody(String code, String message) {
        return """
                {"error":{"code":"%s","message":"%s"}}""".formatted(code, message);
    }
}
