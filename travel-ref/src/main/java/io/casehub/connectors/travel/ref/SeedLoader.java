package io.casehub.connectors.travel.ref;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.travel.model.AccommodationDetail;
import io.casehub.connectors.travel.model.TransportOption;

final class SeedLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    static List<TransportOption> loadTransport() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/transport.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/transport.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<AccommodationDetail> loadAccommodation() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/accommodation.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/accommodation.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
