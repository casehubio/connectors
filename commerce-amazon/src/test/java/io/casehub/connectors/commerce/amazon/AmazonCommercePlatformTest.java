package io.casehub.connectors.commerce.amazon;

import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.commerce.model.CheckoutRequest;
import io.casehub.connectors.commerce.model.OrderStatus;
import io.casehub.connectors.commerce.spi.CommercePlatform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmazonCommercePlatformTest {

    @Test
    void idIsAmazon() {
        var platform = createPlatform();
        assertThat(platform.id()).isEqualTo("amazon");
    }

    @Test
    void supportsProductSearchAndDetailsAndCartAndCheckout() {
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

    @Test
    void cartAddAndViewRetainsItems() {
        var platform = createPlatform();
        var cart = platform.cart("user1");

        cart.addItem("B08TEST1", 2);
        var result = cart.view();

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().productId()).isEqualTo("B08TEST1");
        assertThat(result.items().getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void cartRemoveItem() {
        var platform = createPlatform();
        var cart = platform.cart("user1");

        cart.addItem("B08TEST1", 1);
        cart.addItem("B08TEST2", 3);
        cart.removeItem("B08TEST1");
        var result = cart.view();

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().productId()).isEqualTo("B08TEST2");
    }

    @Test
    void cartClearRemovesAll() {
        var platform = createPlatform();
        var cart = platform.cart("user1");

        cart.addItem("B08TEST1", 1);
        cart.clear();
        var result = cart.view();

        assertThat(result.items()).isEmpty();
    }

    @Test
    void cartIsScopedPerUser() {
        var platform = createPlatform();
        platform.cart("user1").addItem("B08TEST1", 1);
        platform.cart("user2").addItem("B08TEST2", 2);

        assertThat(platform.cart("user1").view().items()).hasSize(1);
        assertThat(platform.cart("user1").view().items().getFirst().productId()).isEqualTo("B08TEST1");
        assertThat(platform.cart("user2").view().items()).hasSize(1);
        assertThat(platform.cart("user2").view().items().getFirst().productId()).isEqualTo("B08TEST2");
    }

    @Test
    void checkoutGeneratesAmazonAddToCartUrl() {
        var platform = createPlatform();
        var cart = platform.cart("user1");
        cart.addItem("B08N5WRWNW", 2);
        cart.addItem("B09ABCDEFG", 1);

        var checkout = platform.checkout("user1");
        var result = checkout.checkout(new CheckoutRequest("cart-user1"));

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.orderId()).contains("amazon.co.uk/gp/aws/cart/add.html");
        assertThat(result.orderId()).contains("ASIN.1=B08N5WRWNW");
        assertThat(result.orderId()).contains("Quantity.1=2");
        assertThat(result.orderId()).contains("ASIN.2=B09ABCDEFG");
        assertThat(result.orderId()).contains("Quantity.2=1");
        assertThat(result.orderId()).contains("AssociateTag=test-21");
    }

    @Test
    void checkoutClearsCart() {
        var platform = createPlatform();
        platform.cart("user1").addItem("B08TEST1", 1);
        platform.checkout("user1").checkout(new CheckoutRequest("cart-user1"));

        assertThat(platform.cart("user1").view().items()).isEmpty();
    }

    @Test
    void addItemToExistingProductIncrementsQuantity() {
        var platform = createPlatform();
        var cart = platform.cart("user1");

        cart.addItem("B08TEST1", 2);
        cart.addItem("B08TEST1", 3);
        var result = cart.view();

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().quantity()).isEqualTo(5);
    }

    private AmazonCommercePlatform createPlatform() {
        return new AmazonCommercePlatform(
            "TESTKEY", "TESTSECRET", "test-21",
            "webservices.amazon.co.uk", "eu-west-1", "www.amazon.co.uk");
    }
}
