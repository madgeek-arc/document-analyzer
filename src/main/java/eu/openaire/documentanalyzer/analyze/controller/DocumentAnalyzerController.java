package eu.openaire.documentanalyzer.analyze.controller;

import com.fasterxml.jackson.databind.JsonNode;
import eu.openaire.documentanalyzer.analyze.service.DocumentAnalyzerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.net.URISyntaxException;
import java.net.URL;

@RestController
@RequestMapping("/v1/documents")
public class DocumentAnalyzerController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DocumentAnalyzerController.class);

    private final DocumentAnalyzerService documentAnalyzerService;

    public DocumentAnalyzerController(DocumentAnalyzerService documentAnalyzerService) {
        this.documentAnalyzerService = documentAnalyzerService;
    }

    @GetMapping(value = "/enrich", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode enrichDocument(@RequestParam URL url, @RequestBody JsonNode jsonTemplate) throws URISyntaxException {
        return documentAnalyzerService.generate(url.toURI(), jsonTemplate);
    }
}
