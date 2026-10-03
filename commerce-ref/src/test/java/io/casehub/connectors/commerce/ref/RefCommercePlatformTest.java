package io.casehub.connectors.commerce.ref;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.commerce.model.CheckoutRequest;
import io.casehub.connectors.commerce.model.OrderStatus;
import io.casehub.connectors.commerce.spi.CommercePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefCommercePlatformTest {

    private RefCommercePlatform platform;

    @BeforeEach
    void setUp() {
        platform = new RefCommercePlatform(new InMemoryCommerceBackend());
    }

    @Test
    void id() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void supportsAllCapabilities() {
        assertThat(platform.supports(CommercePlatform.ProductSearch.class)).isTrue();
        assertThat(platform.supports(CommercePlatform.ProductDetails.class)).isTrue();
        assertThat(platform.supports(CommercePlatform.Cart.class)).isTrue();
        assertThat(platform.supports(CommercePlatform.Checkout.class)).isTrue();
        assertThat(platform.supports(CommercePlatform.OrderTracking.class)).isTrue();
    }

    @Test
    void searchByTextFindsProducts() {
        var results = platform.productSearch("user1")
            .search("headphones", new PageRequest(null, 10));
        assertThat(results.items()).isNotEmpty();
        assertThat(results.items()).allSatisfy(p ->
            assertThat(p.name().toLowerCase() + " " + p.category().toLowerCase())
                .containsIgnoringCase("headphone"));
    }

    @Test
    void searchPaginates() {
        var page1 = platform.productSearch("user1")
            .search("", new PageRequest(null, 3));
        assertThat(page1.items()).hasSize(3);
        assertThat(page1.hasMore()).isTrue();

        var page2 = platform.productSearch("user1")
            .search("", new PageRequest(page1.nextCursor(), 3));
        assertThat(page2.items()).isNotEmpty();
    }

    @Test
    void searchByCategoryFiltersResults() {
        var results = platform.productSearch("user1")
            .searchByCategory("electronics", new PageRequest(null, 20));
        assertThat(results.items()).isNotEmpty();
        assertThat(results.items()).allSatisfy(p ->
            assertThat(p.category().toLowerCase()).contains("electronics"));
    }

    @Test
    void searchByBrandFiltersResults() {
        var results = platform.productSearch("user1")
            .searchByBrand("Sony", new PageRequest(null, 20));
        assertThat(results.items()).isNotEmpty();
        assertThat(results.items()).allSatisfy(p ->
            assertThat(p.brand()).isEqualTo("Sony"));
    }

    @Test
    void getProductDetailReturnsFullInfo() {
        var products = platform.productSearch("user1")
            .search("", new PageRequest(null, 1));
        var productId = products.items().getFirst().id();

        var detail = platform.productDetails("user1").get(productId);
        assertThat(detail.id()).isEqualTo(productId);
        assertThat(detail.name()).isNotBlank();
        assertThat(detail.description()).isNotBlank();
        assertThat(detail.price()).isNotNull();
        assertThat(detail.price().amount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(detail.images()).isNotEmpty();
        assertThat(detail.specifications()).isNotEmpty();
        assertThat(detail.url()).isNotNull();
    }

    @Test
    void getProductDetailThrowsForUnknown() {
        assertThatThrownBy(() -> platform.productDetails("user1").get("unknown"))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void productReviewsReturnsList() {
        var products = platform.productSearch("user1")
            .search("", new PageRequest(null, 1));
        var productId = products.items().getFirst().id();

        var reviews = platform.productDetails("user1")
            .reviews(productId, new PageRequest(null, 10));
        assertThat(reviews).isNotEmpty();
    }

    @Test
    void cartLifecycle() {
        var cart = platform.cart("user1");

        var products = platform.productSearch("user1")
            .search("", new PageRequest(null, 2));
        var p1 = products.items().get(0);
        var p2 = products.items().get(1);

        var afterAdd = cart.addItem(p1.id(), 2);
        assertThat(afterAdd.items()).hasSize(1);
        assertThat(afterAdd.items().getFirst().quantity()).isEqualTo(2);
        assertThat(afterAdd.total().amount()).isGreaterThan(BigDecimal.ZERO);

        var afterSecond = cart.addItem(p2.id(), 1);
        assertThat(afterSecond.items()).hasSize(2);

        var afterRemove = cart.removeItem(p1.id());
        assertThat(afterRemove.items()).hasSize(1);
        assertThat(afterRemove.items().getFirst().productId()).isEqualTo(p2.id());

        var afterClear = cart.clear();
        assertThat(afterClear.items()).isEmpty();
        assertThat(afterClear.total().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void cartIsUserScoped() {
        var products = platform.productSearch("user1")
            .search("", new PageRequest(null, 1));
        var productId = products.items().getFirst().id();

        platform.cart("user1").addItem(productId, 1);
        var user1Cart = platform.cart("user1").view();
        var user2Cart = platform.cart("user2").view();

        assertThat(user1Cart.items()).hasSize(1);
        assertThat(user2Cart.items()).isEmpty();
    }

    @Test
    void checkoutCreatesOrder() {
        var products = platform.productSearch("user1")
            .search("", new PageRequest(null, 1));
        var productId = products.items().getFirst().id();

        platform.cart("user1").addItem(productId, 1);
        var cartView = platform.cart("user1").view();

        var result = platform.checkout("user1")
            .checkout(new CheckoutRequest(cartView.id()));
        assertThat(result.orderId()).isNotBlank();
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.total().amount()).isGreaterThan(BigDecimal.ZERO);

        var emptyCart = platform.cart("user1").view();
        assertThat(emptyCart.items()).isEmpty();
    }

    @Test
    void orderTrackingRetrievesOrder() {
        var products = platform.productSearch("user1")
            .search("", new PageRequest(null, 1));
        platform.cart("user1").addItem(products.items().getFirst().id(), 1);
        var cartView = platform.cart("user1").view();

        var checkoutResult = platform.checkout("user1")
            .checkout(new CheckoutRequest(cartView.id()));

        var order = platform.orderTracking("user1").getOrder(checkoutResult.orderId());
        assertThat(order.id()).isEqualTo(checkoutResult.orderId());
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.lineItems()).isNotEmpty();
    }

    @Test
    void listOrdersShowsHistory() {
        var products = platform.productSearch("user1")
            .search("", new PageRequest(null, 1));
        platform.cart("user1").addItem(products.items().getFirst().id(), 1);
        var cartView = platform.cart("user1").view();
        platform.checkout("user1").checkout(new CheckoutRequest(cartView.id()));

        var orders = platform.orderTracking("user1")
            .listOrders(new PageRequest(null, 10));
        assertThat(orders.items()).isNotEmpty();
    }

    @Test
    void ordersAreUserScoped() {
        var products = platform.productSearch("user1")
            .search("", new PageRequest(null, 1));
        platform.cart("user1").addItem(products.items().getFirst().id(), 1);
        var cartView = platform.cart("user1").view();
        platform.checkout("user1").checkout(new CheckoutRequest(cartView.id()));

        var user1Orders = platform.orderTracking("user1")
            .listOrders(new PageRequest(null, 10));
        var user2Orders = platform.orderTracking("user2")
            .listOrders(new PageRequest(null, 10));

        assertThat(user1Orders.items()).isNotEmpty();
        assertThat(user2Orders.items()).isEmpty();
    }
}
