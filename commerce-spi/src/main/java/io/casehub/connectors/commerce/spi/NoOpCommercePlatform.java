package io.casehub.connectors.commerce.spi;

import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.commerce.model.CheckoutRequest;
import io.casehub.connectors.commerce.model.CheckoutResult;
import io.casehub.connectors.commerce.model.Order;
import io.casehub.connectors.commerce.model.Product;
import io.casehub.connectors.commerce.model.ProductDetail;
import io.casehub.connectors.commerce.model.ProductReview;
import io.casehub.connectors.commerce.model.ShoppingCart;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpCommercePlatform implements CommercePlatform {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return false;
    }

    @Override
    public ProductSearch productSearch(String userId) {
        return NoOpProductSearch.INSTANCE;
    }

    @Override
    public ProductDetails productDetails(String userId) {
        return NoOpProductDetails.INSTANCE;
    }

    @Override
    public Cart cart(String userId) {
        return NoOpCart.INSTANCE;
    }

    @Override
    public Checkout checkout(String userId) {
        return NoOpCheckout.INSTANCE;
    }

    @Override
    public OrderTracking orderTracking(String userId) {
        return NoOpOrderTracking.INSTANCE;
    }

    private enum NoOpProductSearch implements ProductSearch {
        INSTANCE;

        @Override
        public Page<Product> search(String query, PageRequest pagination) {
            throw new UnsupportedCapabilityException("search", "ProductSearch", "none", List.of());
        }

        @Override
        public Page<Product> searchByCategory(String category, PageRequest pagination) {
            throw new UnsupportedCapabilityException("searchByCategory", "ProductSearch", "none",
                List.of());
        }

        @Override
        public Page<Product> searchByBrand(String brand, PageRequest pagination) {
            throw new UnsupportedCapabilityException("searchByBrand", "ProductSearch", "none",
                List.of());
        }
    }

    private enum NoOpProductDetails implements ProductDetails {
        INSTANCE;

        @Override
        public ProductDetail get(String productId) {
            throw new UnsupportedCapabilityException("get", "ProductDetails", "none", List.of());
        }

        @Override
        public List<ProductReview> reviews(String productId, PageRequest pagination) {
            throw new UnsupportedCapabilityException("reviews", "ProductDetails", "none",
                List.of());
        }
    }

    private enum NoOpCart implements Cart {
        INSTANCE;

        @Override
        public ShoppingCart view() {
            throw new UnsupportedCapabilityException("view", "Cart", "none", List.of());
        }

        @Override
        public ShoppingCart addItem(String productId, int quantity) {
            throw new UnsupportedCapabilityException("addItem", "Cart", "none", List.of());
        }

        @Override
        public ShoppingCart removeItem(String productId) {
            throw new UnsupportedCapabilityException("removeItem", "Cart", "none", List.of());
        }

        @Override
        public ShoppingCart clear() {
            throw new UnsupportedCapabilityException("clear", "Cart", "none", List.of());
        }
    }

    private enum NoOpCheckout implements Checkout {
        INSTANCE;

        @Override
        public CheckoutResult checkout(CheckoutRequest request) {
            throw new UnsupportedCapabilityException("checkout", "Checkout", "none", List.of());
        }
    }

    private enum NoOpOrderTracking implements OrderTracking {
        INSTANCE;

        @Override
        public Order getOrder(String orderId) {
            throw new UnsupportedCapabilityException("getOrder", "OrderTracking", "none",
                List.of());
        }

        @Override
        public Page<Order> listOrders(PageRequest pagination) {
            throw new UnsupportedCapabilityException("listOrders", "OrderTracking", "none",
                List.of());
        }
    }
}
