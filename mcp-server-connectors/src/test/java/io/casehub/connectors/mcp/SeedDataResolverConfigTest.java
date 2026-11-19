package io.casehub.connectors.mcp;

import io.casehub.connectors.seed.SeedDataResolver;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class SeedDataResolverConfigTest {

    @Test
    void seedDataResolver_resolves_corpusFiles() {
        var result = SeedDataResolver.resolve("calendar", "calendars.yaml");
        assertThat(result).isPresent();
    }
}
