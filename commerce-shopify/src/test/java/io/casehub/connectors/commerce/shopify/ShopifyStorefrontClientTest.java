package io.casehub.connectors.commerce.shopify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ShopifyStorefrontClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void mapProductNodeExtractsAllFields() throws Exception {
        var json = MAPPER.readTree("""
            {
              "id": "gid://shopify/Product/123",
              "title": "Organic Cotton T-Shirt",
              "vendor": "EcoWear",
              "productType": "Clothing",
              "availableForSale": true,
              "totalInventory": 42,
              "priceRange": {
                "minVariantPrice": {"amount": "24.99", "currencyCode": "GBP"}
              },
              "featuredImage": {
                "url": "https://cdn.shopify.com/tshirt.jpg",
                "altText": "Cotton T-Shirt",
                "width": 800,
                "height": 600
              }
            }
            """);

        var product = ShopifyStorefrontClient.mapProductNode(json);

        assertThat(product.id()).isEqualTo("gid://shopify/Product/123");
        assertThat(product.name()).isEqualTo("Organic Cotton T-Shirt");
        assertThat(product.brand()).isEqualTo("EcoWear");
        assertThat(product.category()).isEqualTo("Clothing");
        assertThat(product.price().amount()).isEqualByComparingTo(new BigDecimal("24.99"));
        assertThat(product.price().currency()).isEqualTo("GBP");
        assertThat(product.inStock()).isTrue();
        assertThat(product.thumbnailUrl()).isEqualTo("https://cdn.shopify.com/tshirt.jpg");
    }

    @Test
    void mapProductNodeHandlesMinimalFields() throws Exception {
        var json = MAPPER.readTree("""
            {
              "id": "gid://shopify/Product/999",
              "title": "Mystery Box",
              "vendor": "",
              "productType": "",
              "availableForSale": false
            }
            """);

        var product = ShopifyStorefrontClient.mapProductNode(json);

        assertThat(product.id()).isEqualTo("gid://shopify/Product/999");
        assertThat(product.name()).isEqualTo("Mystery Box");
        assertThat(product.brand()).isEmpty();
        assertThat(product.inStock()).isFalse();
        assertThat(product.price()).isNull();
    }

    @Test
    void mapCartNodeExtractsItemsAndTotal() throws Exception {
        var json = MAPPER.readTree("""
            {
              "id": "gid://shopify/Cart/abc123",
              "lines": {
                "edges": [{
                  "node": {
                    "id": "gid://shopify/CartLine/line1",
                    "quantity": 2,
                    "merchandise": {
                      "id": "gid://shopify/ProductVariant/v1",
                      "title": "Default",
                      "priceV2": {"amount": "24.99", "currencyCode": "GBP"},
                      "product": {
                        "id": "gid://shopify/Product/123",
                        "title": "Cotton T-Shirt"
                      }
                    }
                  }
                }]
              },
              "cost": {
                "totalAmount": {"amount": "49.98", "currencyCode": "GBP"}
              },
              "checkoutUrl": "https://mystore.myshopify.com/checkout/abc123"
            }
            """);

        var cart = ShopifyStorefrontClient.mapCartNode(json);

        assertThat(cart.id()).isEqualTo("gid://shopify/Cart/abc123");
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().getFirst().productId()).isEqualTo("gid://shopify/Product/123");
        assertThat(cart.items().getFirst().productName()).isEqualTo("Cotton T-Shirt");
        assertThat(cart.items().getFirst().quantity()).isEqualTo(2);
        assertThat(cart.items().getFirst().unitPrice().amount()).isEqualByComparingTo(new BigDecimal("24.99"));
        assertThat(cart.total().amount()).isEqualByComparingTo(new BigDecimal("49.98"));
    }

    @Test
    void buildProductSearchQueryContainsRequiredFields() {
        var query = ShopifyStorefrontClient.buildProductSearchQuery("laptop", null, 10);

        assertThat(query).contains("products(first: 10");
        assertThat(query).contains("query: \"laptop\"");
        assertThat(query).contains("title");
        assertThat(query).contains("vendor");
        assertThat(query).contains("priceRange");
    }

    @Test
    void buildProductSearchQueryWithCursorIncludesAfter() {
        var query = ShopifyStorefrontClient.buildProductSearchQuery("shoes", "cursor123", 5);

        assertThat(query).contains("after: \"cursor123\"");
    }
}
