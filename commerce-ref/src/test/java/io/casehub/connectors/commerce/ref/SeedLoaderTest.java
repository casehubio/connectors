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
        var admiral = products.stream()
                              .filter(p -> p.id().equals("prod-1"))
                              .findFirst().orElseThrow();
        assertThat(admiral.name()).contains("Admiral");
        assertThat(admiral.brand()).isEqualTo("Admiral");
        assertThat(admiral.category()).isEqualTo("insurance");
        assertThat(admiral.price().amount()).isEqualByComparingTo("612.00");
        assertThat(admiral.price().currency()).isEqualTo("GBP");
        assertThat(admiral.reviews()).hasSize(2);
    }

    @Test
    void productsSpanMultipleCategories() {
        var products   = SeedLoader.loadProducts();
        var categories = products.stream().map(p -> p.category()).distinct().toList();
        assertThat(categories).contains("insurance", "electronics", "home", "sports");
    }
}
