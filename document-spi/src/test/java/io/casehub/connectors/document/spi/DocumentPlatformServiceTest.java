package io.casehub.connectors.document.spi;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.document.model.DocumentMetadata;
import io.casehub.connectors.document.model.DocumentSummary;
import io.casehub.connectors.document.model.Folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentPlatformServiceTest {

    @Test
    void platform_registered_returnsIt() {
        var stub = stubPlatform("test");
        var service = new DocumentPlatformService(List.of(stub));

        assertThat(service.platform("test")).isSameAs(stub);
    }

    @Test
    void platform_unknown_throws() {
        var service = new DocumentPlatformService(List.of(stubPlatform("test")));

        assertThatThrownBy(() -> service.platform("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void supports_registered_true() {
        var service = new DocumentPlatformService(List.of(stubPlatform("test")));

        assertThat(service.supports("test")).isTrue();
    }

    @Test
    void supports_unknown_false() {
        var service = new DocumentPlatformService(List.of(stubPlatform("test")));

        assertThat(service.supports("unknown")).isFalse();
    }

    @Test
    void ids_returnsAllRegistered() {
        var service = new DocumentPlatformService(
                List.of(stubPlatform("a"), stubPlatform("b")));

        assertThat(service.ids()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void duplicateId_throws() {
        assertThatThrownBy(() ->
                new DocumentPlatformService(
                        List.of(stubPlatform("dup"), stubPlatform("dup"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    private static DocumentPlatform stubPlatform(String id) {
        return new DocumentPlatform() {
            @Override public String id() { return id; }
            @Override public boolean supports(Class<?> c) { return false; }
            @Override public FileOperations files() { return null; }
            @Override public FolderOperations folders() { return null; }
            @Override public SearchOperations search() { return null; }
            @Override public SharingOperations sharing() { return null; }
        };
    }
}
