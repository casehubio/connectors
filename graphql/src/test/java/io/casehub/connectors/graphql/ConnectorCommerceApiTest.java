package io.casehub.connectors.graphql;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.commerce.model.CheckoutRequest;
import io.casehub.connectors.commerce.model.CheckoutResult;
import io.casehub.connectors.commerce.model.Money;
import io.casehub.connectors.commerce.model.Order;
import io.casehub.connectors.commerce.model.OrderStatus;
import io.casehub.connectors.commerce.model.Product;
import io.casehub.connectors.commerce.model.ProductDetail;
import io.casehub.connectors.commerce.model.ProductReview;
import io.casehub.connectors.commerce.model.ShoppingCart;
import io.casehub.connectors.commerce.spi.CommercePlatform;
import io.casehub.connectors.commerce.spi.CommercePlatformService;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectorCommerceApiTest {

    @Test
    void searchProductsThrowsWhenProductSearchUnsupported() {
        var platform = new StubCommercePlatform("limited", false, false, false, false, false);
        var service = new CommercePlatformService(List.of(platform));
        var api = apiWith(service);

        assertThatThrownBy(() -> api.searchProducts("limited", "headphones", null, null))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("searchProducts");
                    assertThat(uce.capability()).isEqualTo("ProductSearch");
                    assertThat(uce.provider()).isEqualTo("limited");
                    assertThat(uce.supportedCapabilities()).isEmpty();
                });
    }

    @Test
    void checkoutThrowsWhenCheckoutUnsupported() {
        var platform = new StubCommercePlatform("search-only", true, false, false, false, false);
        var service = new CommercePlatformService(List.of(platform));
        var api = apiWith(service);

        assertThatThrownBy(() -> api.checkout("search-only", new CheckoutRequest("cart-1")))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("checkout");
                    assertThat(uce.capability()).isEqualTo("Checkout");
                    assertThat(uce.supportedCapabilities()).containsExactly("ProductSearch");
                });
    }

    @Test
    void searchProductsDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.searchProducts("full-commerce", "headphones", null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().name()).isEqualTo("Wireless Headphones");
    }

    @Test
    void viewCartDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.viewCart("full-commerce");

        assertThat(result).isNotNull();
        assertThat(result.items()).isEmpty();
    }

    @Test
    void listOrdersDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.listOrders("full-commerce", null, null);

        assertThat(result.items()).isEmpty();
    }

    private ConnectorCommerceApi apiWith(CommercePlatformService service) {
        var identity = mock(SecurityIdentity.class);
        var principal = mock(Principal.class);
        when(identity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("test-user");

        var api = new ConnectorCommerceApi();
        api.commerceService = service;
        api.identity = identity;
        return api;
    }

    private CommercePlatformService fullService() {
        return new CommercePlatformService(List.of(new FullStubCommercePlatform()));
    }

    record StubCommercePlatform(String id, boolean search, boolean details,
                                boolean cart, boolean checkout,
                                boolean tracking) implements CommercePlatform {
        @Override public boolean supports(Class<?> c) {
            if (c == ProductSearch.class) return search;
            if (c == ProductDetails.class) return details;
            if (c == Cart.class) return cart;
            if (c == Checkout.class) return checkout;
            if (c == OrderTracking.class) return tracking;
            return false;
        }
        @Override public ProductSearch productSearch(String u) { throw new UnsupportedOperationException(); }
        @Override public ProductDetails productDetails(String u) { throw new UnsupportedOperationException(); }
        @Override public Cart cart(String u) { throw new UnsupportedOperationException(); }
        @Override public Checkout checkout(String u) { throw new UnsupportedOperationException(); }
        @Override public OrderTracking orderTracking(String u) { throw new UnsupportedOperationException(); }
    }

    static class FullStubCommercePlatform implements CommercePlatform {
        @Override public String id() { return "full-commerce"; }
        @Override public boolean supports(Class<?> c) { return true; }

        @Override public ProductSearch productSearch(String u) {
            return new ProductSearch() {
                @Override public Page<Product> search(String query, PageRequest p) {
                    return new Page<>(List.of(new Product("p1", "Wireless Headphones", "Sony", "electronics",
                        new Money(new BigDecimal("29.99"), "USD"), 4.5, 100, true, null)), null, false);
                }
                @Override public Page<Product> searchByCategory(String cat, PageRequest p) {
                    return new Page<>(List.of(), null, false);
                }
                @Override public Page<Product> searchByBrand(String brand, PageRequest p) {
                    return new Page<>(List.of(), null, false);
                }
            };
        }

        @Override public ProductDetails productDetails(String u) {
            return new ProductDetails() {
                @Override public ProductDetail get(String productId) { return null; }
                @Override public List<ProductReview> reviews(String productId, PageRequest p) { return List.of(); }
            };
        }

        @Override public Cart cart(String u) {
            return new Cart() {
                private final Money zero = new Money(BigDecimal.ZERO, "GBP");
                @Override public ShoppingCart view() { return new ShoppingCart("cart-1", List.of(), zero); }
                @Override public ShoppingCart addItem(String pid, int qty) { return view(); }
                @Override public ShoppingCart removeItem(String pid) { return view(); }
                @Override public ShoppingCart clear() { return view(); }
            };
        }

        @Override public Checkout checkout(String u) {
            return request -> new CheckoutResult("order-1", OrderStatus.CONFIRMED,
                new Money(BigDecimal.ZERO, "GBP"));
        }

        @Override public OrderTracking orderTracking(String u) {
            return new OrderTracking() {
                @Override public Order getOrder(String orderId) { return null; }
                @Override public Page<Order> listOrders(PageRequest p) { return new Page<>(List.of(), null, false); }
            };
        }
    }
}
