package io.casehub.connectors.commerce.shopify;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.Page;
import io.casehub.connectors.commerce.model.CartItem;
import io.casehub.connectors.commerce.model.Money;
import io.casehub.connectors.commerce.model.Product;
import io.casehub.connectors.commerce.model.ProductDetail;
import io.casehub.connectors.commerce.model.ProductImage;
import io.casehub.connectors.commerce.model.ShoppingCart;
import io.casehub.connectors.http.HttpHelper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ShopifyStorefrontClient {

    private static final Logger LOG = Logger.getLogger(ShopifyStorefrontClient.class.getName());
    static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String storeDomain;
    private final String accessToken;
    private final String apiVersion;

    public ShopifyStorefrontClient(String storeDomain, String accessToken) {
        this(storeDomain, accessToken, "2024-10");
    }

    ShopifyStorefrontClient(String storeDomain, String accessToken, String apiVersion) {
        this.storeDomain = storeDomain;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
    }

    public Page<Product> searchProducts(String query, String cursor, int limit) {
        var gql = buildProductSearchQuery(query, cursor, limit);
        var data = execute(gql);
        if (data == null) return Page.of(List.of());

        var products = data.path("products");
        var items = new ArrayList<Product>();
        for (var edge : products.path("edges")) {
            items.add(mapProductNode(edge.path("node")));
        }
        var hasMore = products.path("pageInfo").path("hasNextPage").asBoolean(false);
        String nextCursor = null;
        var edges = products.path("edges");
        if (hasMore && edges.isArray() && !edges.isEmpty()) {
            nextCursor = edges.get(edges.size() - 1).path("cursor").asText(null);
        }
        return new Page<>(items, nextCursor, hasMore);
    }

    public ProductDetail getProduct(String productId) {
        var gql = buildProductDetailQuery(productId);
        var data = execute(gql);
        if (data == null) throw new RuntimeException("Failed to fetch product: " + productId);

        var node = data.path("product");
        if (node.isMissingNode()) throw new RuntimeException("Product not found: " + productId);
        return mapProductDetailNode(node);
    }

    public String getDefaultVariantId(String productId) {
        var gql = "{ product(id: \"" + gqlEsc(productId) + "\") { variants(first: 1) { edges { node { id } } } } }";
        var data = execute(gql);
        if (data == null) throw new RuntimeException("Failed to fetch variants for: " + productId);
        return data.at("/product/variants/edges/0/node/id").asText(null);
    }

    public ShoppingCart cartCreate(String variantId, int quantity) {
        var gql = "mutation { cartCreate(input: { lines: [{ merchandiseId: \"" + gqlEsc(variantId)
            + "\", quantity: " + quantity + " }] }) { cart { " + CART_FIELDS + " } } }";
        var data = execute(gql);
        if (data == null) throw new RuntimeException("Failed to create cart");
        return mapCartNode(data.path("cartCreate").path("cart"));
    }

    public ShoppingCart cartLinesAdd(String cartId, String variantId, int quantity) {
        var gql = "mutation { cartLinesAdd(cartId: \"" + gqlEsc(cartId)
            + "\", lines: [{ merchandiseId: \"" + gqlEsc(variantId)
            + "\", quantity: " + quantity + " }]) { cart { " + CART_FIELDS + " } } }";
        var data = execute(gql);
        if (data == null) throw new RuntimeException("Failed to add to cart");
        return mapCartNode(data.path("cartLinesAdd").path("cart"));
    }

    public ShoppingCart cartLinesRemove(String cartId, String lineId) {
        var gql = "mutation { cartLinesRemove(cartId: \"" + gqlEsc(cartId)
            + "\", lineIds: [\"" + gqlEsc(lineId)
            + "\"]) { cart { " + CART_FIELDS + " } } }";
        var data = execute(gql);
        if (data == null) throw new RuntimeException("Failed to remove from cart");
        return mapCartNode(data.path("cartLinesRemove").path("cart"));
    }

    public ShoppingCart getCart(String cartId) {
        var gql = "{ cart(id: \"" + gqlEsc(cartId) + "\") { " + CART_FIELDS + " } }";
        var data = execute(gql);
        if (data == null) throw new RuntimeException("Failed to get cart: " + cartId);
        return mapCartNode(data.path("cart"));
    }

    public String findLineIdForProduct(String cartId, String productId) {
        var gql = "{ cart(id: \"" + gqlEsc(cartId) + "\") { " + CART_FIELDS + " } }";
        var data = execute(gql);
        if (data == null) return null;
        for (var edge : data.path("cart").path("lines").path("edges")) {
            var line = edge.path("node");
            var lineProductId = line.path("merchandise").path("product").path("id").asText("");
            if (lineProductId.equals(productId)) {
                return line.path("id").asText(null);
            }
        }
        return null;
    }

    public String getCheckoutUrl(String cartId) {
        var gql = "{ cart(id: \"" + gqlEsc(cartId) + "\") { checkoutUrl } }";
        var data = execute(gql);
        if (data == null) throw new RuntimeException("Failed to get checkout URL");
        return data.path("cart").path("checkoutUrl").asText(null);
    }

    private JsonNode execute(String query) {
        try {
            var url = "https://" + storeDomain + "/api/" + apiVersion + "/graphql.json";
            var body = "{\"query\":" + MAPPER.writeValueAsString(query) + "}";

            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Shopify-Storefront-Access-Token", accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warning("Shopify Storefront API error: status=" + response.statusCode());
                return null;
            }
            var root = MAPPER.readTree(response.body());
            if (root.has("errors")) {
                LOG.warning("Shopify GraphQL errors: " + root.path("errors"));
                return null;
            }
            return root.path("data");
        } catch (Exception e) {
            LOG.warning("Shopify Storefront API request failed: " + e.getMessage());
            return null;
        }
    }

    static String buildProductSearchQuery(String query, String cursor, int limit) {
        var afterClause = cursor != null ? ", after: \"" + gqlEsc(cursor) + "\"" : "";
        return "{ products(first: " + limit + afterClause + ", query: \"" + gqlEsc(query) + "\") {"
            + " edges { cursor node { " + PRODUCT_FIELDS + " } }"
            + " pageInfo { hasNextPage } } }";
    }

    static String buildProductDetailQuery(String productId) {
        return "{ product(id: \"" + gqlEsc(productId) + "\") {"
            + " id title vendor productType description availableForSale"
            + " variants(first: 10) { edges { node { id title sku priceV2 { amount currencyCode }"
            + " availableForSale quantityAvailable } } }"
            + " images(first: 10) { edges { node { url altText width height } } } } }";
    }

    static Product mapProductNode(JsonNode node) {
        var id = node.path("id").asText();
        var name = node.path("title").asText("");
        var brand = node.path("vendor").asText("");
        var category = node.path("productType").asText("");
        var inStock = node.path("availableForSale").asBoolean(false);

        Money price = null;
        var priceNode = node.path("priceRange").path("minVariantPrice");
        if (!priceNode.isMissingNode() && priceNode.has("amount")) {
            price = new Money(
                new BigDecimal(priceNode.path("amount").asText("0")),
                priceNode.path("currencyCode").asText("GBP"));
        }

        var thumbnailUrl = node.path("featuredImage").path("url").asText(null);

        return new Product(id, name, brand, category, price, null, null, inStock, thumbnailUrl);
    }

    static ProductDetail mapProductDetailNode(JsonNode node) {
        var id = node.path("id").asText();
        var name = node.path("title").asText("");
        var brand = node.path("vendor").asText("");
        var category = node.path("productType").asText("");
        var description = node.path("description").asText(null);
        var inStock = node.path("availableForSale").asBoolean(false);

        Money price = null;
        String sku = null;
        int stockQuantity = 0;
        var variants = node.path("variants").path("edges");
        if (variants.isArray() && !variants.isEmpty()) {
            var firstVariant = variants.get(0).path("node");
            sku = firstVariant.path("sku").asText(null);
            var pv = firstVariant.path("priceV2");
            if (pv.has("amount")) {
                price = new Money(new BigDecimal(pv.path("amount").asText("0")),
                    pv.path("currencyCode").asText("GBP"));
            }
            stockQuantity = firstVariant.path("quantityAvailable").asInt(0);
        }

        var images = new ArrayList<ProductImage>();
        var imagesNode = node.path("images").path("edges");
        if (imagesNode.isArray()) {
            for (var edge : imagesNode) {
                var img = edge.path("node");
                images.add(new ProductImage(
                    img.path("url").asText(""),
                    img.path("altText").asText(null),
                    img.path("width").asInt(0),
                    img.path("height").asInt(0)));
            }
        }

        return new ProductDetail(id, name, brand, sku, category, description, price,
            null, null, inStock, stockQuantity, images, List.of(), Map.of(), null);
    }

    static ShoppingCart mapCartNode(JsonNode node) {
        var cartId = node.path("id").asText();
        var items = new ArrayList<CartItem>();

        for (var edge : node.path("lines").path("edges")) {
            var line = edge.path("node");
            var merch = line.path("merchandise");
            var product = merch.path("product");
            var pv = merch.path("priceV2");
            Money unitPrice = pv.has("amount")
                ? new Money(new BigDecimal(pv.path("amount").asText("0")),
                    pv.path("currencyCode").asText("GBP"))
                : null;

            items.add(new CartItem(
                product.path("id").asText(merch.path("id").asText()),
                product.path("title").asText(merch.path("title").asText("")),
                line.path("quantity").asInt(1),
                unitPrice));
        }

        Money total = null;
        var totalNode = node.path("cost").path("totalAmount");
        if (totalNode.has("amount")) {
            total = new Money(new BigDecimal(totalNode.path("amount").asText("0")),
                totalNode.path("currencyCode").asText("GBP"));
        }

        return new ShoppingCart(cartId, items, total);
    }

    private static final String PRODUCT_FIELDS =
        "id title vendor productType availableForSale totalInventory"
        + " priceRange { minVariantPrice { amount currencyCode } }"
        + " featuredImage { url altText width height }";

    private static final String CART_FIELDS =
        "id lines(first: 50) { edges { node { id quantity merchandise {"
        + " ... on ProductVariant { id title priceV2 { amount currencyCode }"
        + " product { id title } } } } } }"
        + " cost { totalAmount { amount currencyCode } } checkoutUrl";

    private static String gqlEsc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
