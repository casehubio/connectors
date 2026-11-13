package io.casehub.connectors.microsoft;

import io.casehub.connectors.AuthFailureReason;
import io.casehub.connectors.ProviderAuthException;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TokenRetry {

    private TokenRetry() {}

    public static <T> T withTokenRetry(Supplier<String> tokenSupplier,
                                        Function<String, T> operation) {
        try {
            return operation.apply(tokenSupplier.get());
        } catch (ProviderAuthException e) {
            if (e.reason() == AuthFailureReason.TOKEN_EXPIRED) {
                return operation.apply(tokenSupplier.get());
            }
            throw e;
        }
    }

    public static void withTokenRetryVoid(Supplier<String> tokenSupplier,
                                           Consumer<String> operation) {
        try {
            operation.accept(tokenSupplier.get());
        } catch (ProviderAuthException e) {
            if (e.reason() == AuthFailureReason.TOKEN_EXPIRED) {
                operation.accept(tokenSupplier.get());
                return;
            }
            throw e;
        }
    }
}
