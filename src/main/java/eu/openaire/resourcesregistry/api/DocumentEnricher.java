package eu.openaire.resourcesregistry.api;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import eu.openaire.resourcesregistry.enrich.service.ContentEnricher;
import eu.openaire.resourcesregistry.model.Content;
import eu.openaire.resourcesregistry.extract.service.ContentExtractor;
import eu.openaire.resourcesregistry.extract.service.HttpContentReader;
import eu.openaire.resourcesregistry.extract.service.PdfContentExtractor;
import eu.openaire.resourcesregistry.extract.service.WebPageContentExtractor;
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
    private final HttpContentReader contentReader;
    private final ContentEnricher contentEnricher;

    public DocumentEnricher(HttpContentReader contentReader, ContentEnricher contentEnricher) {
        this.contentExtractors.put("webpage", new WebPageContentExtractor());
        this.contentExtractors.put("pdf", new PdfContentExtractor());
        this.contentReader = contentReader;
        this.contentEnricher = contentEnricher;
    }

    public JsonNode generate(URI uri) {
        String type = uri.toString().contains(".pdf") ? "pdf" : "webpage";

        try {
            byte[] raw = contentReader.read(uri);
            Content content = contentExtractors.get(type).extract(raw);

            JsonNode augContent = contentEnricher.enrich(content);
            logger.info(augContent.toPrettyString());
            return augContent;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
