package io.casehub.connectors;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnsupportedCapabilityExceptionTest {

    @Test
    void carriesStructuredMetadata() {
        var ex = new UnsupportedCapabilityException(
                "search", "SearchOperations", "s3",
                List.of("FileOperations"));

        assertThat(ex.operation()).isEqualTo("search");
        assertThat(ex.capability()).isEqualTo("SearchOperations");
        assertThat(ex.provider()).isEqualTo("s3");
        assertThat(ex.supportedCapabilities()).containsExactly("FileOperations");
        assertThat(ex.getMessage()).isEqualTo("s3 does not support SearchOperations");
    }

    @Test
    void supportedCapabilitiesIsImmutable() {
        var mutable = new java.util.ArrayList<>(List.of("A", "B"));
        var ex = new UnsupportedCapabilityException("op", "Cap", "prov", mutable);
        mutable.add("C");
        assertThat(ex.supportedCapabilities()).containsExactly("A", "B");
    }
}
