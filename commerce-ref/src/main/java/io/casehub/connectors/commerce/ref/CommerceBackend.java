package io.casehub.connectors.commerce.ref;

import io.casehub.connectors.commerce.model.CheckoutRequest;
import io.casehub.connectors.commerce.model.CheckoutResult;
import io.casehub.connectors.commerce.model.Order;
import io.casehub.connectors.commerce.model.Product;
import io.casehub.connectors.commerce.model.ProductDetail;
import io.casehub.connectors.commerce.model.ProductReview;
import io.casehub.connectors.commerce.model.ShoppingCart;

import java.util.List;

public interface CommerceBackend {

    List<Product> allProducts();

    List<Product> searchByText(String query);

    List<Product> searchByCategory(String category);

    List<Product> searchByBrand(String brand);

    ProductDetail productDetail(String productId);

    List<ProductReview> reviews(String productId);

    ShoppingCart viewCart(String userId);

    ShoppingCart addToCart(String userId, String productId, int quantity);

    ShoppingCart removeFromCart(String userId, String productId);

    ShoppingCart clearCart(String userId);

    CheckoutResult checkout(String userId, CheckoutRequest request);

    Order getOrder(String userId, String orderId);

    List<Order> listOrders(String userId);
}
