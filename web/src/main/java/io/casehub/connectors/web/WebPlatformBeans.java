package io.casehub.connectors.web;

import io.casehub.connectors.web.spi.ImageDescriber;
import io.casehub.connectors.web.spi.WebPlatform;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class WebPlatformBeans {

    @Produces
    @Singleton
    public WebPlatform webPlatform(WebPlatformConfig config,
                                    Instance<ImageDescriber> imageDescriberInstance) {
        ImageDescriber describer = imageDescriberInstance.isResolvable()
            ? imageDescriberInstance.get() : null;

        var imageProcessor = new WebImageProcessor(describer);
        var interventionQueue = new InterventionQueue(
            Duration.ofMinutes(config.intervention().timeoutMinutes()));

        List<io.casehub.connectors.web.spi.DocumentParser> parsers = List.of(
            new HtmlDocumentParser(),
            new PdfDocumentParser(null, null),
            new ImageDocumentParser(describer),
            new DocxDocumentParser(describer)
        );

        return new DefaultWebPlatform(imageProcessor, interventionQueue, parsers);
    }
}
