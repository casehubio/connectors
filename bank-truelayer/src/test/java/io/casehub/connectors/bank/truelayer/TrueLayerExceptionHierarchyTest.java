package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.AuthFailureReason;
import io.casehub.connectors.ProviderApiException;
import io.casehub.connectors.ProviderAuthException;
import io.casehub.connectors.ProviderRateLimitedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrueLayerExceptionHierarchyTest {

    @Test
    void trueLayerApiException_extendsProviderApiException() {
        var ex = new TrueLayerApiException(500, "Internal error");

        assertThat(ex).isInstanceOf(ProviderApiException.class);
        assertThat(ex.statusCode()).isEqualTo(500);
        assertThat(ex.provider()).isEqualTo("truelayer");
        assertThat(ex.responseBody()).isEqualTo("Internal error");
    }

    @Test
    void consentExpiredException_extendsProviderAuthException() {
        var ex = new ConsentExpiredException("user-42");

        assertThat(ex).isInstanceOf(ProviderAuthException.class);
        assertThat(ex).isInstanceOf(ProviderApiException.class);
        assertThat(ex.provider()).isEqualTo("truelayer");
        assertThat(ex.reason()).isEqualTo(AuthFailureReason.TOKEN_EXPIRED);
        assertThat(ex.statusCode()).isEqualTo(401);
        assertThat(ex.userId()).isEqualTo("user-42");
    }

    @Test
    void rateLimitedException_extendsProviderRateLimitedException() {
        var ex = new RateLimitedException(30);

        assertThat(ex).isInstanceOf(ProviderRateLimitedException.class);
        assertThat(ex).isInstanceOf(ProviderApiException.class);
        assertThat(ex.retryAfterSeconds()).isEqualTo(30);
        assertThat(ex.provider()).isEqualTo("truelayer");
        assertThat(ex.statusCode()).isEqualTo(429);
    }
}
