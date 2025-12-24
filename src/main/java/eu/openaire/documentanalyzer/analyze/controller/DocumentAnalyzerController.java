/*
 * Copyright 2025 OpenAIRE AMKE
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
