package io.casehub.connectors.web.spi;

import io.casehub.connectors.web.model.CapturedImage;
import io.casehub.connectors.web.model.FetchOptions;
import io.casehub.connectors.web.model.FetchResult;
import io.casehub.connectors.web.model.ImageProcessingResult;
import io.casehub.connectors.web.model.InterventionRequest;
import io.casehub.connectors.web.model.ParseResult;
import io.casehub.platform.simulation.SimulationEligible;

import java.util.List;

@SimulationEligible(name = "web-platform",
    capabilities = {"fetch", "parse", "imageCapture", "documentParsing", "interventionManagement"})
public interface WebPlatform {

    String id();

    boolean supports(Class<?> capability);

    Fetch fetch();

    Parse parse();

    ImageCapture imageCapture();

    DocumentParsing documentParsing();

    InterventionManagement interventionManagement();

    interface Fetch {

        FetchResult fetch(String url);

        FetchResult fetch(String url, FetchOptions options);
    }

    interface Parse {

        ParseResult parseHtml(String html, String sourceUrl);
    }

    interface ImageCapture {

        ImageProcessingResult extractImages(String html, String sourceUrl);

        String enrichMarkdown(String markdown, ImageProcessingResult result);
    }

    interface DocumentParsing {

        String parse(byte[] content, String mimeType);

        boolean supportsFormat(String mimeType);
    }

    interface InterventionManagement {

        String request(String url, String reason);

        List<InterventionRequest> pending();

        void resolve(String requestId, String html);
    }
}
