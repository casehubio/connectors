package io.casehub.connectors.web.spi;

import io.casehub.connectors.web.model.FetchOptions;
import io.casehub.connectors.web.model.FetchResult;
import io.casehub.connectors.web.model.ImageProcessingResult;
import io.casehub.connectors.web.model.InterventionRequest;
import io.casehub.connectors.web.model.ParseResult;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpWebPlatform implements WebPlatform {

    static final String NOT_CONFIGURED = "No web provider configured";

    @Override
    public String id() {
        return "none";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return false;
    }

    @Override
    public Fetch fetch() {
        return NoOpFetch.INSTANCE;
    }

    @Override
    public Parse parse() {
        return NoOpParse.INSTANCE;
    }

    @Override
    public ImageCapture imageCapture() {
        return NoOpImageCapture.INSTANCE;
    }

    @Override
    public DocumentParsing documentParsing() {
        return NoOpDocumentParsing.INSTANCE;
    }

    @Override
    public InterventionManagement interventionManagement() {
        return NoOpInterventionManagement.INSTANCE;
    }

    enum NoOpFetch implements Fetch {
        INSTANCE;

        @Override
        public FetchResult fetch(String url) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }

        @Override
        public FetchResult fetch(String url, FetchOptions options) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }
    }

    enum NoOpParse implements Parse {
        INSTANCE;

        @Override
        public ParseResult parseHtml(String html, String sourceUrl) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }
    }

    enum NoOpImageCapture implements ImageCapture {
        INSTANCE;

        @Override
        public ImageProcessingResult extractImages(String html, String sourceUrl) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }

        @Override
        public String enrichMarkdown(String markdown, ImageProcessingResult result) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }
    }

    enum NoOpDocumentParsing implements DocumentParsing {
        INSTANCE;

        @Override
        public String parse(byte[] content, String mimeType) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }

        @Override
        public boolean supportsFormat(String mimeType) {
            return false;
        }
    }

    enum NoOpInterventionManagement implements InterventionManagement {
        INSTANCE;

        @Override
        public String request(String url, String reason) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }

        @Override
        public List<InterventionRequest> pending() {
            return List.of();
        }

        @Override
        public void resolve(String requestId, String html) {
            throw new UnsupportedOperationException(NOT_CONFIGURED);
        }
    }
}
