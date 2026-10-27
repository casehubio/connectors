package io.casehub.connectors.commerce.shopify;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.commerce.model.CheckoutRequest;
import io.casehub.connectors.commerce.model.CheckoutResult;
import io.casehub.connectors.commerce.model.Order;
import io.casehub.connectors.commerce.model.OrderStatus;
import io.casehub.connectors.commerce.model.Product;
import io.casehub.connectors.commerce.model.ProductDetail;
import io.casehub.connectors.commerce.model.ProductReview;
import io.casehub.connectors.commerce.model.ShoppingCart;
import io.casehub.connectors.commerce.spi.CommercePlatform;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ShopifyCommercePlatform implements CommercePlatform {

    private static final Set<Class<?>> SUPPORTED = Set.of(
        ProductSearch.class, ProductDetails.class, Cart.class, Checkout.class);

    private final ShopifyStorefrontClient client;
    private final ConcurrentHashMap<String, String> userCarts = new ConcurrentHashMap<>();

    public ShopifyCommercePlatform(ShopifyStorefrontClient client) {
        this.client = client;
    }

    @Override
    public String id() { return "shopify"; }

    @Override
    public boolean supports(Class<?> capability) { return SUPPORTED.contains(capability); }

    @Override
    public ProductSearch productSearch(String userId) { return new ShopifyProductSearch(); }

    @Override
    public ProductDetails productDetails(String userId) { return new ShopifyProductDetails(); }

    @Override
    public Cart cart(String userId) { return new ShopifyCart(userId); }

    @Override
    public Checkout checkout(String userId) { return new ShopifyCheckout(userId); }

    @Override
    public OrderTracking orderTracking(String userId) {
        throw new UnsupportedCapabilityException("orderTracking", "OrderTracking",
            "shopify", List.of("ProductSearch", "ProductDetails", "Cart", "Checkout"));
    }

    private class ShopifyProductSearch implements ProductSearch {
        @Override
        public Page<Product> search(String query, PageRequest pagination) {
            return client.searchProducts(query, pagination.cursor(), pagination.pageSize());
        }

        @Override
        public Page<Product> searchByCategory(String category, PageRequest pagination) {
            return client.searchProducts("product_type:" + category, pagination.cursor(), pagination.pageSize());
        }

        @Override
        public Page<Product> searchByBrand(String brand, PageRequest pagination) {
            return client.searchProducts("vendor:" + brand, pagination.cursor(), pagination.pageSize());
        }
    }

    private class ShopifyProductDetails implements ProductDetails {
        @Override
        public ProductDetail get(String productId) {
            return client.getProduct(productId);
        }

        @Override
        public List<ProductReview> reviews(String productId, PageRequest pagination) {
            return List.of();
        }
    }

    private class ShopifyCart implements Cart {
        private final String userId;

        ShopifyCart(String userId) { this.userId = userId; }

        @Override
        public ShoppingCart view() {
            var cartId = userCarts.get(userId);
            if (cartId == null) return new ShoppingCart("empty-" + userId, List.of(), null);
            return client.getCart(cartId);
        }

        @Override
        public ShoppingCart addItem(String productId, int quantity) {
            var variantId = client.getDefaultVariantId(productId);
            if (variantId == null) throw new RuntimeException("No variant found for: " + productId);

            var cartId = userCarts.get(userId);
            ShoppingCart result;
            if (cartId == null) {
                result = client.cartCreate(variantId, quantity);
                userCarts.put(userId, result.id());
            } else {
                result = client.cartLinesAdd(cartId, variantId, quantity);
            }
            return result;
        }

        @Override
        public ShoppingCart removeItem(String productId) {
            var cartId = userCarts.get(userId);
            if (cartId == null) return new ShoppingCart("empty-" + userId, List.of(), null);

            var lineId = client.findLineIdForProduct(cartId, productId);
            if (lineId == null) return client.getCart(cartId);

            return client.cartLinesRemove(cartId, lineId);
        }

        @Override
        public ShoppingCart clear() {
            userCarts.remove(userId);
            return new ShoppingCart("empty-" + userId, List.of(), null);
        }
    }

    private class ShopifyCheckout implements Checkout {
        private final String userId;

        ShopifyCheckout(String userId) { this.userId = userId; }

        @Override
        public CheckoutResult checkout(CheckoutRequest request) {
            var cartId = userCarts.get(userId);
            if (cartId == null) throw new IllegalStateException("Cart is empty");

            var checkoutUrl = client.getCheckoutUrl(cartId);
            var cart = client.getCart(cartId);
            userCarts.remove(userId);
            return new CheckoutResult(checkoutUrl, OrderStatus.PENDING, cart.total());
        }
    }
}
