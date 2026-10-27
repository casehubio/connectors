package io.casehub.connectors.commerce.shopify;

import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.commerce.spi.CommercePlatform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShopifyCommercePlatformTest {

    @Test
    void idIsShopify() {
        var platform = createPlatform();
        assertThat(platform.id()).isEqualTo("shopify");
    }

    @Test
    void supportsProductSearchDetailsCartAndCheckout() {
        var platform = createPlatform();
        assertThat(platform.supports(CommercePlatform.ProductSearch.class)).isTrue();
        assertThat(platform.supports(CommercePlatform.ProductDetails.class)).isTrue();
        assertThat(platform.supports(CommercePlatform.Cart.class)).isTrue();
        assertThat(platform.supports(CommercePlatform.Checkout.class)).isTrue();
    }

    @Test
    void doesNotSupportOrderTracking() {
        var platform = createPlatform();
        assertThat(platform.supports(CommercePlatform.OrderTracking.class)).isFalse();
    }

    @Test
    void orderTrackingThrowsUnsupported() {
        var platform = createPlatform();
        assertThatThrownBy(() -> platform.orderTracking("user1"))
            .isInstanceOf(UnsupportedCapabilityException.class);
    }

    private ShopifyCommercePlatform createPlatform() {
        var client = new ShopifyStorefrontClient("test.myshopify.com", "test-token");
        return new ShopifyCommercePlatform(client);
    }
}
