package io.casehub.connectors.commerce.amazon;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.commerce.model.CartItem;
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

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AmazonCommercePlatform implements CommercePlatform {

    private static final Set<Class<?>> SUPPORTED = Set.of(
        ProductSearch.class, ProductDetails.class, Cart.class, Checkout.class);

    private final AmazonPaApiClient client;
    private final String partnerTag;
    private final String marketplace;
    private final ConcurrentHashMap<String, List<CartItem>> carts = new ConcurrentHashMap<>();

    public AmazonCommercePlatform(String accessKey, String secretKey, String partnerTag,
                                   String host, String region, String marketplace) {
        this.client = new AmazonPaApiClient(accessKey, secretKey, partnerTag, host, region, marketplace);
        this.partnerTag = partnerTag;
        this.marketplace = marketplace;
    }

    @Override
    public String id() { return "amazon"; }

    @Override
    public boolean supports(Class<?> capability) { return SUPPORTED.contains(capability); }

    @Override
    public ProductSearch productSearch(String userId) { return new AmazonProductSearch(); }

    @Override
    public ProductDetails productDetails(String userId) { return new AmazonProductDetails(); }

    @Override
    public Cart cart(String userId) { return new AmazonCart(userId); }

    @Override
    public Checkout checkout(String userId) { return new AmazonCheckout(userId); }

    @Override
    public OrderTracking orderTracking(String userId) {
        throw new UnsupportedCapabilityException("orderTracking", "OrderTracking",
            "amazon", List.of("ProductSearch", "ProductDetails", "Cart", "Checkout"));
    }

    private class AmazonProductSearch implements ProductSearch {
        @Override
        public Page<Product> search(String query, PageRequest pagination) {
            return client.searchItems(query);
        }

        @Override
        public Page<Product> searchByCategory(String category, PageRequest pagination) {
            return client.searchItems(category);
        }

        @Override
        public Page<Product> searchByBrand(String brand, PageRequest pagination) {
            return client.searchItems(brand);
        }
    }

    private class AmazonProductDetails implements ProductDetails {
        @Override
        public ProductDetail get(String productId) {
            return client.getItem(productId);
        }

        @Override
        public List<ProductReview> reviews(String productId, PageRequest pagination) {
            return List.of();
        }
    }

    private class AmazonCart implements Cart {
        private final String userId;

        AmazonCart(String userId) { this.userId = userId; }

        @Override
        public ShoppingCart view() { return buildCart(userId); }

        @Override
        public ShoppingCart addItem(String productId, int quantity) {
            var items = new ArrayList<>(carts.getOrDefault(userId, new ArrayList<>()));
            var existing = items.stream().filter(i -> i.productId().equals(productId)).findFirst();
            if (existing.isPresent()) {
                var old = existing.get();
                items.remove(old);
                items.add(new CartItem(productId, old.productName(),
                    old.quantity() + quantity, old.unitPrice()));
            } else {
                items.add(new CartItem(productId, productId, quantity, null));
            }
            carts.put(userId, items);
            return buildCart(userId);
        }

        @Override
        public ShoppingCart removeItem(String productId) {
            var items = new ArrayList<>(carts.getOrDefault(userId, new ArrayList<>()));
            items.removeIf(i -> i.productId().equals(productId));
            carts.put(userId, items);
            return buildCart(userId);
        }

        @Override
        public ShoppingCart clear() {
            carts.put(userId, new ArrayList<>());
            return buildCart(userId);
        }
    }

    private class AmazonCheckout implements Checkout {
        private final String userId;

        AmazonCheckout(String userId) { this.userId = userId; }

        @Override
        public CheckoutResult checkout(CheckoutRequest request) {
            var items = carts.getOrDefault(userId, List.of());
            if (items.isEmpty()) throw new IllegalStateException("Cart is empty");

            var url = buildAddToCartUrl(items);
            var total = calculateTotal(items);
            carts.put(userId, new ArrayList<>());
            return new CheckoutResult(url, OrderStatus.PENDING, total);
        }
    }

    private String buildAddToCartUrl(List<CartItem> items) {
        var host = marketplace.replace("www.", "");
        var sb = new StringBuilder("https://www." + host + "/gp/aws/cart/add.html?AssociateTag=")
            .append(enc(partnerTag));
        for (int i = 0; i < items.size(); i++) {
            var n = i + 1;
            sb.append("&ASIN.").append(n).append('=').append(enc(items.get(i).productId()));
            sb.append("&Quantity.").append(n).append('=').append(items.get(i).quantity());
        }
        return sb.toString();
    }

    private ShoppingCart buildCart(String userId) {
        var items = carts.getOrDefault(userId, List.of());
        return new ShoppingCart("cart-" + userId, List.copyOf(items), calculateTotal(items));
    }

    private Money calculateTotal(List<CartItem> items) {
        var total = items.stream()
            .filter(i -> i.unitPrice() != null)
            .map(i -> i.unitPrice().amount().multiply(BigDecimal.valueOf(i.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Money(total, "GBP");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
