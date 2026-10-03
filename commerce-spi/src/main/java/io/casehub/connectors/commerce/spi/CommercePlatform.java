package io.casehub.connectors.commerce.spi;

import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.commerce.model.CheckoutRequest;
import io.casehub.connectors.commerce.model.CheckoutResult;
import io.casehub.connectors.commerce.model.Order;
import io.casehub.connectors.commerce.model.Product;
import io.casehub.connectors.commerce.model.ProductDetail;
import io.casehub.connectors.commerce.model.ProductReview;
import io.casehub.connectors.commerce.model.ShoppingCart;
import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "commerce-platform",
    capabilities = {"productSearch", "productDetails", "cart", "checkout", "orderTracking"})
public interface CommercePlatform {

    String id();

    boolean supports(Class<?> capability);

    ProductSearch productSearch(String userId);

    ProductDetails productDetails(String userId);

    Cart cart(String userId);

    Checkout checkout(String userId);

    OrderTracking orderTracking(String userId);

    interface ProductSearch {

        Page<Product> search(String query, PageRequest pagination);

        Page<Product> searchByCategory(String category, PageRequest pagination);

        Page<Product> searchByBrand(String brand, PageRequest pagination);
    }

    interface ProductDetails {

        ProductDetail get(String productId);

        List<ProductReview> reviews(String productId, PageRequest pagination);
    }

    interface Cart {

        ShoppingCart view();

        ShoppingCart addItem(String productId, int quantity);

        ShoppingCart removeItem(String productId);

        ShoppingCart clear();
    }

    interface Checkout {

        CheckoutResult checkout(CheckoutRequest request);
    }

    interface OrderTracking {

        Order getOrder(String orderId);

        Page<Order> listOrders(PageRequest pagination);
    }
}
