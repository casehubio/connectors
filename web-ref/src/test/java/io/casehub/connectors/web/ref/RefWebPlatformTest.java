package io.casehub.connectors.web.ref;

import io.casehub.connectors.web.model.FetchResult;
import io.casehub.connectors.web.model.ParseResult;
import io.casehub.connectors.web.spi.WebPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefWebPlatformTest {

    private RefWebPlatform platform;
    private InMemoryWebBackend backend;

    @BeforeEach
    void setUp() {
        backend = new InMemoryWebBackend();
        platform = new RefWebPlatform(backend);
    }

    @Test
    void idReturnsRef() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void supportsAllCapabilities() {
        assertThat(platform.supports(WebPlatform.Fetch.class)).isTrue();
        assertThat(platform.supports(WebPlatform.Parse.class)).isTrue();
        assertThat(platform.supports(WebPlatform.ImageCapture.class)).isTrue();
        assertThat(platform.supports(WebPlatform.DocumentParsing.class)).isTrue();
        assertThat(platform.supports(WebPlatform.InterventionManagement.class)).isTrue();
    }

    @Test
    void fetchReturns404ForUnknownUrl() {
        FetchResult result = platform.fetch().fetch("https://example.com/missing");
        assertThat(result.statusCode()).isEqualTo(404);
    }

    @Test
    void fetchReturnsRegisteredHtml() {
        backend.registerUrl("https://example.com", "<html><body>Hello</body></html>");
        FetchResult result = platform.fetch().fetch("https://example.com");
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.html()).contains("Hello");
    }

    @Test
    void parseReturnsMarkdown() {
        ParseResult result = platform.parse().parseHtml("<p>Hello</p>", "https://example.com");
        assertThat(result.positionalMarkdown()).contains("Hello");
    }

    @Test
    void imageCaptureReturnsEmpty() {
        assertThat(platform.imageCapture().extractImages("<html></html>", "https://example.com").images()).isEmpty();
    }

    @Test
    void enrichMarkdownPassesThrough() {
        var result = platform.imageCapture().extractImages("<html></html>", "https://example.com");
        assertThat(platform.imageCapture().enrichMarkdown("# Title", result)).isEqualTo("# Title");
    }

    @Test
    void documentParsingSupportsAllFormats() {
        assertThat(platform.documentParsing().supportsFormat("application/pdf")).isTrue();
        assertThat(platform.documentParsing().supportsFormat("image/png")).isTrue();
    }

    @Test
    void documentParsingReturnsContent() {
        String result = platform.documentParsing().parse("Hello".getBytes(), "text/plain");
        assertThat(result).isEqualTo("Hello");
    }

    @Test
    void interventionRequestAndResolve() {
        String id = platform.interventionManagement().request("https://paywall.com", "paywall");
        assertThat(platform.interventionManagement().pending()).hasSize(1);

        platform.interventionManagement().resolve(id, "<html>Resolved</html>");
        assertThat(platform.interventionManagement().pending()).isEmpty();

        FetchResult result = platform.fetch().fetch("https://paywall.com");
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.html()).contains("Resolved");
    }
}
