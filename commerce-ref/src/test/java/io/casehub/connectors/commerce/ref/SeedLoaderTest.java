package io.casehub.connectors.commerce.ref;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadsAllProducts() {
        var products = SeedLoader.loadProducts();
        assertThat(products).hasSize(8);
    }

    @Test
    void firstProductHasCorrectFields() {
        var products = SeedLoader.loadProducts();
        var sony = products.stream()
            .filter(p -> p.id().equals("prod-1"))
            .findFirst().orElseThrow();
        assertThat(sony.name()).isEqualTo("Sony WH-1000XM5 Headphones");
        assertThat(sony.brand()).isEqualTo("Sony");
        assertThat(sony.category()).isEqualTo("electronics");
        assertThat(sony.price().amount()).isEqualByComparingTo("299.99");
        assertThat(sony.price().currency()).isEqualTo("GBP");
        assertThat(sony.reviews()).hasSize(2);
        assertThat(sony.specifications()).containsEntry("connectivity", "Bluetooth 5.2");
    }

    @Test
    void outOfStockProductLoadsCorrectly() {
        var products = SeedLoader.loadProducts();
        var marshall = products.stream()
            .filter(p -> p.name().contains("Marshall"))
            .findFirst().orElseThrow();
        assertThat(marshall.inStock()).isFalse();
        assertThat(marshall.stockQuantity()).isZero();
    }
}
