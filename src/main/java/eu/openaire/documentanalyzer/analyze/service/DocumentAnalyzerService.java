package eu.openaire.documentanalyzer.analyze.service;


import com.fasterxml.jackson.databind.JsonNode;
import eu.openaire.documentanalyzer.common.model.Content;
import eu.openaire.documentanalyzer.enrich.service.DocumentContentProcessor;
import eu.openaire.documentanalyzer.extract.service.ContentExtractor;
import eu.openaire.documentanalyzer.extract.service.HttpUriReader;
import eu.openaire.documentanalyzer.extract.service.PdfContentExtractor;
import eu.openaire.documentanalyzer.extract.service.WebPageContentExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class DocumentAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAnalyzerService.class);

    private final Map<String, ContentExtractor> contentExtractors = new HashMap<>();
    private final HttpUriReader contentReader;
    private final DocumentContentProcessor contentProcessor;

    public DocumentAnalyzerService(DocumentContentProcessor contentProcessor) throws NoSuchAlgorithmException, KeyManagementException {
        this.contentExtractors.put("webpage", new WebPageContentExtractor());
        this.contentExtractors.put("pdf", new PdfContentExtractor());
        this.contentReader = new HttpUriReader();
        this.contentProcessor = contentProcessor;
    }

    public Content read(URI uri) {
        String type = uri.toString().contains(".pdf") ? "pdf" : "webpage";
        try {
            byte[] raw = contentReader.read(uri);
            return contentExtractors.get(type).extract(raw);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonNode generate(Content content, JsonNode template) {
        JsonNode augContent = contentProcessor.generate(template, content);
        logger.info(augContent.toPrettyString());
        return augContent;
    }

    public JsonNode generate(URI uri, JsonNode template) {
        Content content = read(uri);
        return generate(content, template);
    }
}
