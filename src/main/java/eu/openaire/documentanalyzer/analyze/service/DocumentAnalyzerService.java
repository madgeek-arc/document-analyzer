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

package eu.openaire.documentanalyzer.analyze.service;


import com.fasterxml.jackson.databind.JsonNode;
import eu.openaire.documentanalyzer.common.model.Content;
import eu.openaire.documentanalyzer.enrich.service.DocumentContentProcessor;
import eu.openaire.documentanalyzer.extract.service.HttpUriReader;
import eu.openaire.documentanalyzer.extract.service.PdfContentExtractor;
import eu.openaire.documentanalyzer.extract.service.WebPageContentExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class DocumentAnalyzerService implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAnalyzerService.class);

    private final WebPageContentExtractor webPageExtractor;
    private final PdfContentExtractor pdfExtractor;
    private final HttpUriReader contentReader;
    private final DocumentContentProcessor contentProcessor;

    public DocumentAnalyzerService(DocumentContentProcessor contentProcessor) throws NoSuchAlgorithmException, KeyManagementException {
        this.webPageExtractor = new WebPageContentExtractor();
        this.pdfExtractor = new PdfContentExtractor();
        this.contentReader = new HttpUriReader();
        this.contentProcessor = contentProcessor;
    }

    public Content read(URI uri) {
        try {
            if (uri.toString().contains(".pdf")) {
                byte[] raw = contentReader.read(uri);
                return pdfExtractor.extract(raw);
            } else {
                return webPageExtractor.extractFromUrl(uri.toString());
            }
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

    @Override
    public void close() {
        webPageExtractor.close();
    }
}
