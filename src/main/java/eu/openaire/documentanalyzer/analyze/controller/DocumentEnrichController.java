package eu.openaire.documentanalyzer.analyze.controller;

import com.fasterxml.jackson.databind.JsonNode;
import eu.openaire.documentanalyzer.analyze.service.DocumentEnricher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.net.URISyntaxException;
import java.net.URL;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentEnrichController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DocumentEnrichController.class);

    private final DocumentEnricher documentEnricher;

    public DocumentEnrichController(DocumentEnricher documentEnricher) {
        this.documentEnricher = documentEnricher;
    }

    @GetMapping(value = "/enrich", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode enrichDocument(@RequestParam URL url, @RequestBody JsonNode jsonTemplate) throws URISyntaxException {
        return documentEnricher.generate(url.toURI(), jsonTemplate);
    }
}
