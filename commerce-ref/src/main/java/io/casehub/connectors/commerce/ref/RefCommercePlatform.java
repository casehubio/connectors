package io.casehub.connectors.commerce.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.PaginationHelper;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.commerce.model.CheckoutRequest;
import io.casehub.connectors.commerce.model.CheckoutResult;
import io.casehub.connectors.commerce.model.Order;
import io.casehub.connectors.commerce.model.Product;
import io.casehub.connectors.commerce.model.ProductDetail;
import io.casehub.connectors.commerce.model.ProductReview;
import io.casehub.connectors.commerce.model.ShoppingCart;
import io.casehub.connectors.commerce.spi.CommercePlatform;

import java.util.List;
import java.util.Set;

public class RefCommercePlatform implements CommercePlatform {

    private static final Set<Class<?>> SUPPORTED = Set.of(
        ProductSearch.class, ProductDetails.class,
        Cart.class, Checkout.class, OrderTracking.class
    );

    private final CommerceBackend backend;

    public RefCommercePlatform(CommerceBackend backend) {
        this.backend = backend;
    }

    @Override
    public String id() {
        return "ref";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return SUPPORTED.contains(capability);
    }

    @Override
    public ProductSearch productSearch(String userId) {
        return new RefProductSearch();
    }

    @Override
    public ProductDetails productDetails(String userId) {
        return new RefProductDetails();
    }

    @Override
    public Cart cart(String userId) {
        return new RefCart(userId);
    }

    @Override
    public Checkout checkout(String userId) {
        return new RefCheckout(userId);
    }

    @Override
    public OrderTracking orderTracking(String userId) {
        return new RefOrderTracking(userId);
    }

    private class RefProductSearch implements ProductSearch {

        @Override
        public Page<Product> search(String query, PageRequest pagination) {
            return PaginationHelper.paginate(backend.searchByText(query), pagination);
        }

        @Override
        public Page<Product> searchByCategory(String category, PageRequest pagination) {
            return PaginationHelper.paginate(backend.searchByCategory(category), pagination);
        }

        @Override
        public Page<Product> searchByBrand(String brand, PageRequest pagination) {
            return PaginationHelper.paginate(backend.searchByBrand(brand), pagination);
        }
    }

    private class RefProductDetails implements ProductDetails {

        @Override
        public ProductDetail get(String productId) {
            return backend.productDetail(productId);
        }

        @Override
        public List<ProductReview> reviews(String productId, PageRequest pagination) {
            return backend.reviews(productId);
        }
    }

    private class RefCart implements Cart {

        private final String userId;

        RefCart(String userId) {
            this.userId = userId;
        }

        @Override
        public ShoppingCart view() {
            return backend.viewCart(userId);
        }

        @Override
        public ShoppingCart addItem(String productId, int quantity) {
            return backend.addToCart(userId, productId, quantity);
        }

        @Override
        public ShoppingCart removeItem(String productId) {
            return backend.removeFromCart(userId, productId);
        }

        @Override
        public ShoppingCart clear() {
            return backend.clearCart(userId);
        }
    }

    private class RefCheckout implements Checkout {

        private final String userId;

        RefCheckout(String userId) {
            this.userId = userId;
        }

        @Override
        public CheckoutResult checkout(CheckoutRequest request) {
            return backend.checkout(userId, request);
        }
    }

    private class RefOrderTracking implements OrderTracking {

        private final String userId;

        RefOrderTracking(String userId) {
            this.userId = userId;
        }

        @Override
        public Order getOrder(String orderId) {
            return backend.getOrder(userId, orderId);
        }

        @Override
        public Page<Order> listOrders(PageRequest pagination) {
            return PaginationHelper.paginate(backend.listOrders(userId), pagination);
        }
    }

}
