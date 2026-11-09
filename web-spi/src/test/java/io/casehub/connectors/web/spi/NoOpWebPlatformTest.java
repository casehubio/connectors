package io.casehub.connectors.web.spi;

import io.casehub.connectors.web.model.BrowserCookie;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpWebPlatformTest {

    private final NoOpWebPlatform platform = new NoOpWebPlatform();

    @Test
    void idReturnsNone() {
        assertThat(platform.id()).isEqualTo("none");
    }

    @Test
    void supportsReturnsFalseForAllCapabilities() {
        assertThat(platform.supports(WebPlatform.Fetch.class)).isFalse();
        assertThat(platform.supports(WebPlatform.Parse.class)).isFalse();
        assertThat(platform.supports(WebPlatform.ImageCapture.class)).isFalse();
        assertThat(platform.supports(WebPlatform.DocumentParsing.class)).isFalse();
        assertThat(platform.supports(WebPlatform.InterventionManagement.class)).isFalse();
        assertThat(platform.supports(WebPlatform.SessionTransfer.class)).isFalse();
    }

    @Test
    void fetchThrowsUnsupported() {
        assertThatThrownBy(() -> platform.fetch().fetch("https://example.com"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("No web provider configured");
    }

    @Test
    void parseThrowsUnsupported() {
        assertThatThrownBy(() -> platform.parse().parseHtml("<html></html>", "https://example.com"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void imageCaptureThrowsUnsupported() {
        assertThatThrownBy(() -> platform.imageCapture().extractImages("<html></html>", "https://example.com"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void documentParsingReturnsFalseForSupportsFormat() {
        assertThat(platform.documentParsing().supportsFormat("application/pdf")).isFalse();
    }

    @Test
    void documentParsingThrowsUnsupported() {
        assertThatThrownBy(() -> platform.documentParsing().parse(new byte[0], "application/pdf"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void interventionPendingReturnsEmpty() {
        assertThat(platform.interventionManagement().pending()).isEmpty();
    }

    @Test
    void interventionRequestThrowsUnsupported() {
        assertThatThrownBy(() -> platform.interventionManagement().request("https://example.com", "paywall"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sessionTransferThrowsUnsupported() {
        assertThatThrownBy(() -> platform.sessionTransfer().transferSession(
                List.of(BrowserCookie.of("sid", "abc", "example.com"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sessionTransferHasNoActiveSession() {
        assertThat(platform.sessionTransfer().hasActiveSession()).isFalse();
    }
}
