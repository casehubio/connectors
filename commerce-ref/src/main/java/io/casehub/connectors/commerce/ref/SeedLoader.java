package io.casehub.connectors.commerce.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.commerce.model.ProductDetail;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

final class SeedLoader {

    private static final ObjectMapper YAML = YamlMappers.create()
            .registerModule(new JavaTimeModule());

    static List<ProductDetail> loadProducts() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/products.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/products.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
