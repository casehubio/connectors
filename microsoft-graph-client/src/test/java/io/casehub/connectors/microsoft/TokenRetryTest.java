package io.casehub.connectors.microsoft;

import io.casehub.connectors.AuthFailureReason;
import io.casehub.connectors.ProviderAuthException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class TokenRetryTest {

    @Test
    void returnsResultOnFirstAttempt() {
        var result = TokenRetry.withTokenRetry(() -> "token", t -> "ok:" + t);
        assertThat(result).isEqualTo("ok:token");
    }

    @Test
    void retriesOnTokenExpired() {
        var calls = new AtomicInteger(0);
        var result = TokenRetry.withTokenRetry(
                () -> "token-" + calls.incrementAndGet(),
                token -> {
                    if (token.equals("token-1")) {
                        throw new ProviderAuthException(AuthFailureReason.TOKEN_EXPIRED, "microsoft");
                    }
                    return "ok:" + token;
                });
        assertThat(result).isEqualTo("ok:token-2");
    }

    @Test
    void propagatesNonTokenExpiredAuthException() {
        assertThatThrownBy(() -> TokenRetry.withTokenRetry(
                () -> "token",
                t -> { throw new ProviderAuthException(AuthFailureReason.FORBIDDEN, "microsoft"); }))
                .isInstanceOf(ProviderAuthException.class)
                .satisfies(e -> assertThat(((ProviderAuthException) e).reason())
                        .isEqualTo(AuthFailureReason.FORBIDDEN));
    }

    @Test
    void propagatesNonAuthExceptions() {
        assertThatThrownBy(() -> TokenRetry.withTokenRetry(
                () -> "token",
                t -> { throw new RuntimeException("boom"); }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
    }

    @Test
    void voidVariantRetries() {
        var calls = new AtomicInteger(0);
        TokenRetry.withTokenRetryVoid(
                () -> "token-" + calls.incrementAndGet(),
                token -> {
                    if (token.equals("token-1")) {
                        throw new ProviderAuthException(AuthFailureReason.TOKEN_EXPIRED, "microsoft");
                    }
                });
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void voidVariantPropagatesForbidden() {
        assertThatThrownBy(() -> TokenRetry.withTokenRetryVoid(
                () -> "token",
                t -> { throw new ProviderAuthException(AuthFailureReason.FORBIDDEN, "microsoft"); }))
                .isInstanceOf(ProviderAuthException.class);
    }
}
