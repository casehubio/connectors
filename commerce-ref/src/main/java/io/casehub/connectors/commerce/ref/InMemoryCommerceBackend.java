package io.casehub.connectors.commerce.ref;

import io.casehub.connectors.commerce.model.CartItem;
import io.casehub.connectors.commerce.model.CheckoutRequest;
import io.casehub.connectors.commerce.model.CheckoutResult;
import io.casehub.connectors.commerce.model.Money;
import io.casehub.connectors.commerce.model.Order;
import io.casehub.connectors.commerce.model.OrderLineItem;
import io.casehub.connectors.commerce.model.OrderStatus;
import io.casehub.connectors.commerce.model.Product;
import io.casehub.connectors.commerce.model.ProductDetail;
import io.casehub.connectors.commerce.model.ProductReview;
import io.casehub.connectors.commerce.model.ShoppingCart;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@DefaultBean
@ApplicationScoped
public class InMemoryCommerceBackend implements CommerceBackend {

    private final Map<String, Product> products = new LinkedHashMap<>();
    private final Map<String, ProductDetail> details = new LinkedHashMap<>();
    private final Map<String, List<ProductReview>> reviews = new LinkedHashMap<>();
    private final Map<String, List<CartItem>> carts = new ConcurrentHashMap<>();
    private final Map<String, List<Order>> orders = new ConcurrentHashMap<>();
    private final AtomicInteger orderIdSeq = new AtomicInteger();
    private int idSeq = 0;

    InMemoryCommerceBackend() {
        seed();
    }

    private void seed() {
        SeedLoader.loadProducts().forEach(d -> {
            var thumbnailUrl = d.images().isEmpty() ? null : d.images().getFirst().url();
            products.put(d.id(), new Product(d.id(), d.name(), d.brand(), d.category(),
                                             d.price(), d.rating(), d.reviewCount(), d.inStock(), thumbnailUrl));
            details.put(d.id(), d);
            reviews.put(d.id(), new ArrayList<>(d.reviews()));
        });
        idSeq = products.size();
    }

    @Override
    public List<Product> allProducts() {
        return List.copyOf(products.values());
    }

    @Override
    public List<Product> searchByText(String query) {
        if (query == null || query.isBlank()) return allProducts();
        var q = query.toLowerCase();
        return products.values().stream()
            .filter(p -> matchesText(p, q))
            .toList();
    }

    @Override
    public List<Product> searchByCategory(String category) {
        var cat = category.toLowerCase();
        return products.values().stream()
            .filter(p -> p.category().toLowerCase().contains(cat))
            .toList();
    }

    @Override
    public List<Product> searchByBrand(String brand) {
        return products.values().stream()
            .filter(p -> p.brand().equalsIgnoreCase(brand))
            .toList();
    }

    @Override
    public ProductDetail productDetail(String productId) {
        var detail = details.get(productId);
        if (detail == null) throw new NoSuchElementException("Product not found: " + productId);
        return detail;
    }

    @Override
    public List<ProductReview> reviews(String productId) {
        var r = reviews.get(productId);
        if (r == null) throw new NoSuchElementException("Product not found: " + productId);
        return List.copyOf(r);
    }

    @Override
    public ShoppingCart viewCart(String userId) {
        var items = carts.getOrDefault(userId, List.of());
        return buildCart(userId, items);
    }

    @Override
    public ShoppingCart addToCart(String userId, String productId, int quantity) {
        var product = products.get(productId);
        if (product == null) throw new NoSuchElementException("Product not found: " + productId);

        var items = new ArrayList<>(carts.getOrDefault(userId, new ArrayList<>()));
        var existing = items.stream()
            .filter(i -> i.productId().equals(productId))
            .findFirst();
        if (existing.isPresent()) {
            var old = existing.get();
            items.remove(old);
            items.add(new CartItem(productId, product.name(),
                old.quantity() + quantity, product.price()));
        } else {
            items.add(new CartItem(productId, product.name(), quantity, product.price()));
        }
        carts.put(userId, items);
        return buildCart(userId, items);
    }

    @Override
    public ShoppingCart removeFromCart(String userId, String productId) {
        var items = new ArrayList<>(carts.getOrDefault(userId, new ArrayList<>()));
        items.removeIf(i -> i.productId().equals(productId));
        carts.put(userId, items);
        return buildCart(userId, items);
    }

    @Override
    public ShoppingCart clearCart(String userId) {
        carts.put(userId, new ArrayList<>());
        return buildCart(userId, List.of());
    }

    @Override
    public CheckoutResult checkout(String userId, CheckoutRequest request) {
        var items = carts.getOrDefault(userId, List.of());
        if (items.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }
        var total = calculateTotal(items);
        var lineItems = items.stream()
            .map(i -> new OrderLineItem(i.productId(), i.productName(),
                i.quantity(), i.unitPrice()))
            .toList();
        var orderId = "order-" + orderIdSeq.incrementAndGet();
        var now = Instant.now();
        var order = new Order(orderId, OrderStatus.CONFIRMED, lineItems, total,
            now, now, null, null);
        orders.computeIfAbsent(userId, k -> new ArrayList<>()).add(order);
        carts.put(userId, new ArrayList<>());
        return new CheckoutResult(orderId, OrderStatus.CONFIRMED, total);
    }

    @Override
    public Order getOrder(String userId, String orderId) {
        return orders.getOrDefault(userId, List.of()).stream()
            .filter(o -> o.id().equals(orderId))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
    }

    @Override
    public List<Order> listOrders(String userId) {
        return List.copyOf(orders.getOrDefault(userId, List.of()));
    }

    private ShoppingCart buildCart(String userId, List<CartItem> items) {
        var cartId = "cart-" + userId;
        var total = calculateTotal(items);
        return new ShoppingCart(cartId, List.copyOf(items), total);
    }

    private Money calculateTotal(List<CartItem> items) {
        var total = items.stream()
            .map(i -> i.unitPrice().amount().multiply(BigDecimal.valueOf(i.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Money(total, "GBP");
    }

    private boolean matchesText(Product product, String query) {
        if (product.name().toLowerCase().contains(query)) return true;
        if (product.brand().toLowerCase().contains(query)) return true;
        return product.category().toLowerCase().contains(query);
    }
}
