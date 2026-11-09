package io.casehub.connectors.web;

import io.casehub.connectors.web.model.CapturedImage;
import io.casehub.connectors.web.model.FetchOptions;
import io.casehub.connectors.web.model.FetchResult;
import io.casehub.connectors.web.model.ImageProcessingResult;
import io.casehub.connectors.web.model.InterventionRequest;
import io.casehub.connectors.web.model.ParseResult;
import io.casehub.connectors.web.spi.DocumentParserConfig;
import io.casehub.connectors.web.spi.WebPlatform;

import java.io.ByteArrayInputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultWebPlatform implements WebPlatform {

    private static final Logger LOG = Logger.getLogger(DefaultWebPlatform.class.getName());
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HtmlDocumentParser htmlParser;
    private final PositionalMarkdownBuilder markdownBuilder;
    private final WebImageProcessor imageProcessor;
    private final InterventionQueue interventionQueue;
    private final List<io.casehub.connectors.web.spi.DocumentParser> documentParsers;

    public DefaultWebPlatform(WebImageProcessor imageProcessor,
                               InterventionQueue interventionQueue,
                               List<io.casehub.connectors.web.spi.DocumentParser> documentParsers) {
        this.htmlParser = new HtmlDocumentParser();
        this.markdownBuilder = new PositionalMarkdownBuilder();
        this.imageProcessor = imageProcessor;
        this.interventionQueue = interventionQueue;
        this.documentParsers = documentParsers != null ? documentParsers : List.of();
    }

    @Override
    public String id() {
        return "default";
    }

    private static final Set<Class<?>> SUPPORTED = Set.of(
        Fetch.class, Parse.class, ImageCapture.class,
        DocumentParsing.class, InterventionManagement.class
    );

    @Override
    public boolean supports(Class<?> capability) {
        return SUPPORTED.contains(capability);
    }

    @Override
    public Fetch fetch() {
        return new DefaultFetch();
    }

    @Override
    public Parse parse() {
        return (html, sourceUrl) -> htmlParser.parse(
            new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
            sourceUrl, DocumentParserConfig.defaults());
    }

    @Override
    public ImageCapture imageCapture() {
        return new DefaultImageCapture();
    }

    @Override
    public DocumentParsing documentParsing() {
        return new DefaultDocumentParsing();
    }

    @Override
    public InterventionManagement interventionManagement() {
        return new DefaultInterventionManagement();
    }

    static class DefaultFetch implements Fetch {

        @Override
        public FetchResult fetch(String url) {
            return fetch(url, null);
        }

        @Override
        public FetchResult fetch(String url, FetchOptions options) {
            try {
                var clientBuilder = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10));

                if (options != null && options.cookies() != null && !options.cookies().isEmpty()) {
                    CookieManager cookieManager = new CookieManager();
                    URI uri = URI.create(url);
                    for (var entry : options.cookies().entrySet()) {
                        HttpCookie cookie = new HttpCookie(entry.getKey(), entry.getValue());
                        cookie.setDomain(uri.getHost());
                        cookie.setPath("/");
                        cookieManager.getCookieStore().add(uri, cookie);
                    }
                    clientBuilder.cookieHandler(cookieManager);
                }

                HttpClient client = clientBuilder.build();
                Duration timeout = (options != null && options.timeout() != null)
                    ? options.timeout() : DEFAULT_TIMEOUT;

                var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .GET();

                if (options != null && options.headers() != null) {
                    options.headers().forEach(requestBuilder::header);
                }

                HttpResponse<String> response = client.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

                String contentType = response.headers()
                    .firstValue("content-type").orElse("text/html");

                return new FetchResult(response.body(), response.statusCode(),
                    contentType, response.headers().map());

            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to fetch " + url, e);
                return new FetchResult("", 0, "text/html", Map.of());
            }
        }
    }

    class DefaultImageCapture implements ImageCapture {
        @Override
        public ImageProcessingResult extractImages(String html, String sourceUrl) {
            if (imageProcessor == null) return ImageProcessingResult.EMPTY;
            return imageProcessor.process(html, sourceUrl);
        }

        @Override
        public String enrichMarkdown(String markdown, ImageProcessingResult result) {
            if (imageProcessor == null) return markdown;
            return imageProcessor.enrichMarkdown(markdown, result);
        }
    }

    class DefaultDocumentParsing implements DocumentParsing {
        @Override
        public String parse(byte[] content, String mimeType) {
            for (var parser : documentParsers) {
                if (parser.supportedMediaTypes().contains(mimeType)) {
                    ParseResult result = parser.parse(
                        new ByteArrayInputStream(content), null, DocumentParserConfig.defaults());
                    return result.positionalMarkdown();
                }
            }
            return new String(content, StandardCharsets.UTF_8);
        }

        @Override
        public boolean supportsFormat(String mimeType) {
            return documentParsers.stream()
                .anyMatch(p -> p.supportedMediaTypes().contains(mimeType));
        }
    }

    class DefaultInterventionManagement implements InterventionManagement {
        @Override
        public String request(String url, String reason) {
            return interventionQueue.submit(url, reason, null).id();
        }

        @Override
        public List<InterventionRequest> pending() {
            return interventionQueue.pending();
        }

        @Override
        public void resolve(String requestId, String html) {
            interventionQueue.resolve(requestId);
        }
    }
}
