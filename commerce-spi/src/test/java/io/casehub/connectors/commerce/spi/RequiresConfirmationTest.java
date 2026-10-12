package io.casehub.connectors.commerce.spi;

import io.casehub.connectors.commerce.model.CheckoutRequest;
import io.casehub.platform.api.confirmation.RequiresConfirmation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequiresConfirmationTest {

    @Test
    void checkoutRequiresConfirmation() throws Exception {
        var method = CommercePlatform.Checkout.class.getMethod("checkout", CheckoutRequest.class);
        var annotation = method.getAnnotation(RequiresConfirmation.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.summary())
                .isEqualTo("Checkout cart ${request.cartId}");
    }
}
