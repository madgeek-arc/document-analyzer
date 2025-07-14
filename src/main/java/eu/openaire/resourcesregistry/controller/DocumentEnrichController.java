package eu.openaire.resourcesregistry.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URISyntaxException;
import java.net.URL;

@RestController
@RequestMapping("/api/v1/enricher")
public class DocumentEnrichController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DocumentEnrichController.class);

    private final DocumentEnricher documentEnricher;

    public DocumentEnrichController(DocumentEnricher documentEnricher) {
        this.documentEnricher = documentEnricher;
    }

    @GetMapping(value = "/documents", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode enrichDocument(@RequestParam URL url) throws URISyntaxException {
        return documentEnricher.generate(url.toURI());
    }
}
