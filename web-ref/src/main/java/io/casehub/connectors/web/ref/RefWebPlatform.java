package io.casehub.connectors.web.ref;

import io.casehub.connectors.web.model.BrowserCookie;
import io.casehub.connectors.web.model.FetchOptions;
import io.casehub.connectors.web.model.FetchResult;
import io.casehub.connectors.web.model.ImageProcessingResult;
import io.casehub.connectors.web.model.InterventionRequest;
import io.casehub.connectors.web.model.Layout;
import io.casehub.connectors.web.model.PanelDefinition;
import io.casehub.connectors.web.model.ParseResult;
import io.casehub.connectors.web.spi.WebPlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RefWebPlatform implements WebPlatform {

    private final InMemoryWebBackend backend;
    private final Fetch fetchOps;
    private final Parse parseOps;
    private final ImageCapture imageCaptureOps;
    private final DocumentParsing documentParsingOps;
    private final InterventionManagement interventionOps;
    private final SessionTransfer sessionTransferOps;

    public RefWebPlatform(InMemoryWebBackend backend) {
        this.backend = backend;
        this.fetchOps = new RefFetch(backend);
        this.parseOps = new RefParse();
        this.imageCaptureOps = new RefImageCapture();
        this.documentParsingOps = new RefDocumentParsing();
        this.interventionOps = new RefInterventionManagement(backend);
        this.sessionTransferOps = new RefSessionTransfer();
    }

    @Override
    public String id() {
        return "ref";
    }

    private static final Set<Class<?>> SUPPORTED = Set.of(
        Fetch.class, Parse.class, ImageCapture.class,
        DocumentParsing.class, InterventionManagement.class,
        SessionTransfer.class
    );

    @Override
    public boolean supports(Class<?> capability) {
        return SUPPORTED.contains(capability);
    }

    @Override public Fetch fetch() { return fetchOps; }
    @Override public Parse parse() { return parseOps; }
    @Override public ImageCapture imageCapture() { return imageCaptureOps; }
    @Override public DocumentParsing documentParsing() { return documentParsingOps; }
    @Override public InterventionManagement interventionManagement() { return interventionOps; }
    @Override public SessionTransfer sessionTransfer() { return sessionTransferOps; }

    public InMemoryWebBackend backend() { return backend; }

    static class RefFetch implements Fetch {
        private final InMemoryWebBackend backend;

        RefFetch(InMemoryWebBackend backend) { this.backend = backend; }

        @Override
        public FetchResult fetch(String url) {
            return fetch(url, null);
        }

        @Override
        public FetchResult fetch(String url, FetchOptions options) {
            String html = backend.fetchHtml(url);
            if (html == null) {
                return new FetchResult("", 404, "text/html", Map.of());
            }
            return new FetchResult(html, 200, "text/html", Map.of());
        }
    }

    static class RefParse implements Parse {
        @Override
        public ParseResult parseHtml(String html, String sourceUrl) {
            Layout layout = new Layout("single",
                List.of(new PanelDefinition("main", 0.0, 0.0, 1.0, 1.0)), 1, new int[]{612, 792});
            return new ParseResult(html, layout, Map.of(), List.of());
        }
    }

    static class RefImageCapture implements ImageCapture {
        @Override
        public ImageProcessingResult extractImages(String html, String sourceUrl) {
            return new ImageProcessingResult(List.of());
        }

        @Override
        public String enrichMarkdown(String markdown, ImageProcessingResult result) {
            return markdown;
        }
    }

    static class RefDocumentParsing implements DocumentParsing {
        @Override
        public String parse(byte[] content, String mimeType) {
            return new String(content);
        }

        @Override
        public boolean supportsFormat(String mimeType) {
            return true;
        }
    }

    static class RefInterventionManagement implements InterventionManagement {
        private final InMemoryWebBackend backend;

        RefInterventionManagement(InMemoryWebBackend backend) { this.backend = backend; }

        @Override
        public String request(String url, String reason) {
            return backend.requestIntervention(url, reason);
        }

        @Override
        public List<InterventionRequest> pending() {
            return backend.pendingInterventions();
        }

        @Override
        public void resolve(String requestId, String html) {
            backend.resolveIntervention(requestId, html);
        }
    }

    static class RefSessionTransfer implements SessionTransfer {
        private final List<BrowserCookie> cookies = new ArrayList<>();

        @Override
        public void transferSession(List<BrowserCookie> cookies) {
            this.cookies.clear();
            this.cookies.addAll(cookies);
        }

        @Override
        public void clearSession() {
            cookies.clear();
        }

        @Override
        public boolean hasActiveSession() {
            return !cookies.isEmpty();
        }
    }
}
