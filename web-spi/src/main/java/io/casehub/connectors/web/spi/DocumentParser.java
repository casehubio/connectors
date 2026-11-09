package io.casehub.connectors.web.spi;

import io.casehub.connectors.web.model.ParseResult;

import java.io.InputStream;
import java.util.Set;

public interface DocumentParser {
    ParseResult parse(InputStream content, String sourceRef, DocumentParserConfig config);
    Set<String> supportedMediaTypes();
}
