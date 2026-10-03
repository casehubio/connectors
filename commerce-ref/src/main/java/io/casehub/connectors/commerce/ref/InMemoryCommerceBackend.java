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
import io.casehub.connectors.commerce.model.ProductImage;
import io.casehub.connectors.commerce.model.ProductReview;
import io.casehub.connectors.commerce.model.ShoppingCart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class InMemoryCommerceBackend implements CommerceBackend {

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
        addProduct("Sony WH-1000XM5 Headphones", "Sony", "prod-sony-xm5",
            "electronics", "Premium noise-cancelling wireless headphones with 30-hour battery life.",
            new Money(new BigDecimal("299.99"), "GBP"), 4.7, 2350, true, 45,
            List.of(new ProductImage("https://example.com/sony-xm5-1.jpg", "Sony WH-1000XM5", 800, 600)),
            List.of(
                new ProductReview("Alice S.", 5.0, "Best noise cancelling I've ever used", 1727900000000L),
                new ProductReview("Bob J.", 4.0, "Great sound, slightly tight fit", 1727800000000L)),
            Map.of("type", "Over-ear", "connectivity", "Bluetooth 5.2",
                "battery", "30 hours", "weight", "250g"));

        addProduct("Kindle Paperwhite", "Amazon", "prod-kindle-pw",
            "electronics", "6.8-inch display with adjustable warm light.",
            new Money(new BigDecimal("139.99"), "GBP"), 4.6, 18000, true, 120,
            List.of(new ProductImage("https://example.com/kindle-pw-1.jpg", "Kindle Paperwhite", 800, 600)),
            List.of(new ProductReview("Carol S.", 5.0, "Perfect for reading on the tube", 1727700000000L)),
            Map.of("display", "6.8-inch glare-free", "storage", "16 GB",
                "battery", "Up to 10 weeks", "waterproof", "IPX8"));

        addProduct("The Thursday Murder Club", "Richard Osman", "prod-tmc-book",
            "books", "A cosy mystery novel set in a Kent retirement village.",
            new Money(new BigDecimal("8.99"), "GBP"), 4.3, 45000, true, 500,
            List.of(new ProductImage("https://example.com/tmc-1.jpg", "The Thursday Murder Club", 400, 600)),
            List.of(new ProductReview("Dave W.", 4.0, "Charming and witty", 1727600000000L)),
            Map.of("format", "Paperback", "pages", "400", "publisher", "Penguin",
                "isbn", "978-0241988268"));

        addProduct("Le Creuset Dutch Oven", "Le Creuset", "prod-lc-oven",
            "home", "Classic 4.5 qt round Dutch oven in Marseille blue.",
            new Money(new BigDecimal("259.00"), "GBP"), 4.8, 8500, true, 15,
            List.of(new ProductImage("https://example.com/lc-oven-1.jpg", "Le Creuset Dutch Oven", 800, 800)),
            List.of(
                new ProductReview("Eve B.", 5.0, "Worth every penny, cooks beautifully", 1727500000000L),
                new ProductReview("Frank L.", 5.0, "A kitchen staple", 1727400000000L)),
            Map.of("capacity", "4.5 qt", "material", "Enamelled cast iron",
                "colour", "Marseille", "dishwasher_safe", "Yes"));

        addProduct("Barbour Bedale Jacket", "Barbour", "prod-barbour-bedale",
            "clothing", "Classic wax jacket, a British countryside essential.",
            new Money(new BigDecimal("219.00"), "GBP"), 4.5, 3200, true, 30,
            List.of(new ProductImage("https://example.com/barbour-1.jpg", "Barbour Bedale", 600, 800)),
            List.of(new ProductReview("Grace C.", 5.0, "Keeps me dry on dog walks", 1727300000000L)),
            Map.of("material", "Waxed cotton", "lining", "Cotton",
                "closure", "Zip and press-stud", "country_of_origin", "England"));

        addProduct("Dyson V15 Detect", "Dyson", "prod-dyson-v15",
            "home", "Cordless vacuum with laser dust detection.",
            new Money(new BigDecimal("599.99"), "GBP"), 4.4, 5600, true, 22,
            List.of(new ProductImage("https://example.com/dyson-v15-1.jpg", "Dyson V15 Detect", 800, 800)),
            List.of(new ProductReview("Hank M.", 4.0, "Impressive tech, heavy for stairs", 1727200000000L)),
            Map.of("runtime", "60 minutes", "bin_capacity", "0.76L",
                "weight", "3.1 kg", "filtration", "Whole-machine HEPA"));

        addProduct("Penguin Classics Box Set", "Various", "prod-penguin-box",
            "books", "20 essential Penguin Classics in a collector's box.",
            new Money(new BigDecimal("79.99"), "GBP"), 4.9, 1200, true, 40,
            List.of(new ProductImage("https://example.com/penguin-box-1.jpg", "Penguin Classics Box Set", 800, 600)),
            List.of(new ProductReview("Ivy D.", 5.0, "Beautiful editions, perfect gift", 1727100000000L)),
            Map.of("format", "Paperback box set", "volumes", "20",
                "publisher", "Penguin Classics"));

        addProduct("Marshall Stanmore III Speaker", "Marshall", "prod-marshall-iii",
            "electronics", "Bluetooth home speaker with iconic Marshall design.",
            new Money(new BigDecimal("329.99"), "GBP"), 4.6, 2100, false, 0,
            List.of(new ProductImage("https://example.com/marshall-iii-1.jpg", "Marshall Stanmore III", 800, 600)),
            List.of(new ProductReview("Jack R.", 5.0, "Looks and sounds incredible", 1727000000000L)),
            Map.of("connectivity", "Bluetooth 5.2", "power", "80W",
                "dimensions", "350 x 195 x 185 mm"));
    }

    private void addProduct(String name, String brand, String sku,
                            String category, String description,
                            Money price, double rating, int reviewCount,
                            boolean inStock, int stockQuantity,
                            List<ProductImage> images, List<ProductReview> productReviews,
                            Map<String, String> specs) {
        var id = "prod-" + (++idSeq);
        var thumbnailUrl = images.isEmpty() ? null : images.getFirst().url();
        products.put(id, new Product(id, name, brand, category, price,
            rating, reviewCount, inStock, thumbnailUrl));
        details.put(id, new ProductDetail(id, name, brand, sku, category,
            description, price, rating, reviewCount, inStock, stockQuantity,
            images, productReviews, specs,
            "https://shop.example.com/product/" + id));
        reviews.put(id, new ArrayList<>(productReviews));
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
