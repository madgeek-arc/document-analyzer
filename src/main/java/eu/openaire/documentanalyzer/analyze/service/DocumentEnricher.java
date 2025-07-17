package eu.openaire.documentanalyzer.analyze.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import eu.openaire.documentanalyzer.common.model.Content;
import eu.openaire.documentanalyzer.enrich.service.DocumentContentProcessor;
import eu.openaire.documentanalyzer.extract.service.ContentExtractor;
import eu.openaire.documentanalyzer.extract.service.HttpUriReader;
import eu.openaire.documentanalyzer.extract.service.PdfContentExtractor;
import eu.openaire.documentanalyzer.extract.service.WebPageContentExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Service
public class DocumentEnricher {

    private static final Logger logger = LoggerFactory.getLogger(DocumentEnricher.class);

    private final Map<String, ContentExtractor> contentExtractors = new HashMap<>();
    private final HttpUriReader contentReader;
    private final DocumentContentProcessor contentEnricher;

    public DocumentEnricher(HttpUriReader contentReader, DocumentContentProcessor contentEnricher) {
        this.contentExtractors.put("webpage", new WebPageContentExtractor());
        this.contentExtractors.put("pdf", new PdfContentExtractor());
        this.contentReader = contentReader;
        this.contentEnricher = contentEnricher;
    }

    public JsonNode generate(URI uri, JsonNode template) {
        String type = uri.toString().contains(".pdf") ? "pdf" : "webpage";

        try {
            byte[] raw = contentReader.read(uri);
            Content content = contentExtractors.get(type).extract(raw);

            JsonNode augContent = contentEnricher.generate(template, content);
            logger.info(augContent.toPrettyString());
            return augContent;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
