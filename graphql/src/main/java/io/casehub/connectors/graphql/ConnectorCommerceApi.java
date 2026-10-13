package io.casehub.connectors.graphql;

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
import io.casehub.connectors.commerce.spi.CommercePlatform;
import io.casehub.connectors.commerce.spi.CommercePlatformService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.util.ArrayList;
import java.util.List;

@McpDomain(value = "connectors/commerce", app = "connectors",
    basePath = "/api/connectors/commerce",
    summary = "Commerce connector — products, cart, checkout, orders")
@ApplicationScoped
public class ConnectorCommerceApi {

    @Inject CommercePlatformService commerceService;
    @Inject SecurityIdentity identity;

    private String userId() {
        return identity.getPrincipal().getName();
    }

    @PlatformQuery("Search products by text query")
    @RestPath("/products")
    public Page<Product> searchProducts(
            @QueryParam("platform") String platform,
            @QueryParam("query") String query,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        CommercePlatform p = commerceService.platform(platform);
        requireCapability(p, CommercePlatform.ProductSearch.class, "searchProducts");
        int size = pageSize != null ? pageSize : 20;
        return p.productSearch(userId()).search(query, new PageRequest(cursor, size));
    }

    @PlatformQuery("Search products by category")
    @RestPath("/products/category/{category}")
    public Page<Product> searchByCategory(
            @QueryParam("platform") String platform,
            @PathParam String category,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        CommercePlatform p = commerceService.platform(platform);
        requireCapability(p, CommercePlatform.ProductSearch.class, "searchByCategory");
        int size = pageSize != null ? pageSize : 20;
        return p.productSearch(userId()).searchByCategory(category, new PageRequest(cursor, size));
    }

    @PlatformQuery("Search products by brand")
    @RestPath("/products/brand/{brand}")
    public Page<Product> searchByBrand(
            @QueryParam("platform") String platform,
            @PathParam String brand,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        CommercePlatform p = commerceService.platform(platform);
        requireCapability(p, CommercePlatform.ProductSearch.class, "searchByBrand");
        int size = pageSize != null ? pageSize : 20;
        return p.productSearch(userId()).searchByBrand(brand, new PageRequest(cursor, size));
    }

    @PlatformQuery("Get full product details")
    @RestPath("/products/{productId}")
    public ProductDetail getProduct(
            @QueryParam("platform") String platform,
            @PathParam String productId) {
        CommercePlatform p = commerceService.platform(platform);
        requireCapability(p, CommercePlatform.ProductDetails.class, "getProduct");
        return p.productDetails(userId()).get(productId);
    }

    @PlatformQuery("Get product reviews")
    @RestPath("/products/{productId}/reviews")
    public List<ProductReview> getProductReviews(
            @QueryParam("platform") String platform,
            @PathParam String productId,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        CommercePlatform p = commerceService.platform(platform);
        requireCapability(p, CommercePlatform.ProductDetails.class, "getProductReviews");
        int size = pageSize != null ? pageSize : 20;
        return p.productDetails(userId()).reviews(productId, new PageRequest(cursor, size));
    }

    @PlatformQuery("View current shopping cart")
    @RestPath("/cart")
    public ShoppingCart viewCart(@QueryParam("platform") String platform) {
        CommercePlatform p = commerceService.platform(platform);
        requireCapability(p, CommercePlatform.Cart.class, "viewCart");
        return p.cart(userId()).view();
    }

    @PlatformMutation("Add item to shopping cart")
    @RestPath("/cart/items")
    public ShoppingCart addToCart(
            @QueryParam("platform") String platform,
            @QueryParam("productId") String productId,
            @QueryParam("quantity") Integer quantity) {
        CommercePlatform p = commerceService.platform(platform);
        requireCapability(p, CommercePlatform.Cart.class, "addToCart");
        int qty = quantity != null ? quantity : 1;
        return p.cart(userId()).addItem(productId, qty);
    }

    @PlatformMutation("Remove item from shopping cart")
    @RestPath("/cart/items/{productId}")
    public ShoppingCart removeFromCart(
            @QueryParam("platform") String platform,
            @PathParam String productId) {
        CommercePlatform p = commerceService.platform(platform);
        requireCapability(p, CommercePlatform.Cart.class, "removeFromCart");
        return p.cart(userId()).removeItem(productId);
    }

    @PlatformMutation("Clear all items from shopping cart")
    @RestPath("/cart/clear")
    public ShoppingCart clearCart(@QueryParam("platform") String platform) {
        CommercePlatform p = commerceService.platform(platform);
        requireCapability(p, CommercePlatform.Cart.class, "clearCart");
        return p.cart(userId()).clear();
    }

    @PlatformMutation("Checkout — purchase items in cart")
    @RestPath("/checkout")
    public CheckoutResult checkout(
            @QueryParam("platform") String platform,
            CheckoutRequest request) {
        CommercePlatform p = commerceService.platform(platform);
        requireCapability(p, CommercePlatform.Checkout.class, "checkout");
        return p.checkout(userId()).checkout(request);
    }

    @PlatformQuery("Get order by ID")
    @RestPath("/orders/{orderId}")
    public Order getOrder(
            @QueryParam("platform") String platform,
            @PathParam String orderId) {
        CommercePlatform p = commerceService.platform(platform);
        requireCapability(p, CommercePlatform.OrderTracking.class, "getOrder");
        return p.orderTracking(userId()).getOrder(orderId);
    }

    @PlatformQuery("List orders")
    @RestPath("/orders")
    public Page<Order> listOrders(
            @QueryParam("platform") String platform,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        CommercePlatform p = commerceService.platform(platform);
        requireCapability(p, CommercePlatform.OrderTracking.class, "listOrders");
        int size = pageSize != null ? pageSize : 20;
        return p.orderTracking(userId()).listOrders(new PageRequest(cursor, size));
    }

    private static void requireCapability(CommercePlatform platform, Class<?> capability, String operation) {
        if (!platform.supports(capability)) {
            var supported = new ArrayList<String>();
            if (platform.supports(CommercePlatform.ProductSearch.class)) supported.add("ProductSearch");
            if (platform.supports(CommercePlatform.ProductDetails.class)) supported.add("ProductDetails");
            if (platform.supports(CommercePlatform.Cart.class)) supported.add("Cart");
            if (platform.supports(CommercePlatform.Checkout.class)) supported.add("Checkout");
            if (platform.supports(CommercePlatform.OrderTracking.class)) supported.add("OrderTracking");
            throw new UnsupportedCapabilityException(
                    operation, capability.getSimpleName(), platform.id(), supported);
        }
    }
}
