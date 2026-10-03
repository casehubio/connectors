package io.casehub.connectors.commerce.ref;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.commerce.model.CheckoutRequest;
import io.casehub.connectors.commerce.model.OrderStatus;
import io.casehub.connectors.commerce.spi.CommercePlatform;
import io.casehub.connectors.commerce.spi.CommercePlatformService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class CommerceIntegrationTest {

    @Inject
    CommercePlatformService service;

    @Inject
    CommercePlatform platform;

    @Test
    void serviceDiscoversRefPlatform() {
        assertThat(service.ids()).contains("ref");
        assertThat(service.platform("ref").id()).isEqualTo("ref");
    }

    @Test
    void injectedPlatformIsRef() {
        assertThat(platform.id()).isEqualTo("ref");
        assertThat(platform.supports(CommercePlatform.ProductSearch.class)).isTrue();
        assertThat(platform.supports(CommercePlatform.Cart.class)).isTrue();
    }

    @Test
    void fullLifecycle_searchToOrderTracking() {
        var search = platform.productSearch("user1");
        var products = search.search("Sony", new PageRequest(null, 10));
        assertThat(products.items()).isNotEmpty();
        var product = products.items().getFirst();

        var detail = platform.productDetails("user1").get(product.id());
        assertThat(detail.name()).isEqualTo(product.name());
        assertThat(detail.price().amount()).isGreaterThan(BigDecimal.ZERO);

        var cart = platform.cart("user1");
        cart.addItem(product.id(), 2);
        var cartView = cart.view();
        assertThat(cartView.items()).hasSize(1);
        assertThat(cartView.items().getFirst().quantity()).isEqualTo(2);

        var result = platform.checkout("user1")
            .checkout(new CheckoutRequest(cartView.id()));
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.total().amount()).isGreaterThan(BigDecimal.ZERO);

        var order = platform.orderTracking("user1").getOrder(result.orderId());
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.lineItems()).hasSize(1);
        assertThat(order.lineItems().getFirst().productId()).isEqualTo(product.id());

        var emptyCart = cart.view();
        assertThat(emptyCart.items()).isEmpty();

        var orders = platform.orderTracking("user1")
            .listOrders(new PageRequest(null, 10));
        assertThat(orders.items()).contains(order);
    }
}
