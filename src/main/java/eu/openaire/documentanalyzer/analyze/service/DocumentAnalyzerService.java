/*
 * Copyright 2026 OpenAIRE AMKE
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
import eu.openaire.documentanalyzer.extract.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class DocumentAnalyzerService implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAnalyzerService.class);

    private final WebPageContentExtractor webPageExtractor;
    private final PdfContentExtractor pdfExtractor;
    private final HttpUriReader contentReader;
    private final DocumentContentProcessor contentProcessor;

    public DocumentAnalyzerService(WebPageContentExtractor webPageExtractor,
                                   PdfContentExtractor pdfExtractor,
                                   HttpUriReader contentReader,
                                   DocumentContentProcessor contentProcessor) {
        this.webPageExtractor = webPageExtractor;
        this.pdfExtractor = pdfExtractor;
        this.contentReader = contentReader;
        this.contentProcessor = contentProcessor;
    }

    public DocumentAnalyzerService(DocumentContentProcessor contentProcessor, long requestDelayMs) throws NoSuchAlgorithmException, KeyManagementException {
        this(new WebPageContentExtractor(requestDelayMs),
             new PdfContentExtractor(),
             new HttpUriReader(requestDelayMs),
             contentProcessor);
    }

    public Content read(URI uri) {
        return read(uri, List.of(), SupplementaryUrlFilterMethod.SIMPLE);
    }

    public Content read(URI uri, List<String> topics, SupplementaryUrlFilterMethod filterMethod) {
        try {
            UriReader.Data raw = contentReader.read(uri);
            if (uri.toString().contains(".pdf")) {
                return pdfExtractor.extract(raw.data());
            } else {
                return webPageExtractor.extractWholeSite(raw.uri(), topics, filterMethod);
            }
        } catch (DeferredContentException e) {
            try {
                return webPageExtractor.extractFromUrl(uri.toString());
            } catch (IOException inner) {
                throw new RuntimeException(inner);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException("Could not parse sitemap.", e);
        }
    }

    public JsonNode generate(Content content, JsonNode template) {
        JsonNode augContent = contentProcessor.generate(template, content);
        logger.info(augContent.toPrettyString());
        return augContent;
    }

    public JsonNode generate(URI uri, JsonNode template) {
        return generate(uri, template, List.of(), SupplementaryUrlFilterMethod.SIMPLE);
    }

    public JsonNode generate(URI uri, JsonNode template, List<String> topics, SupplementaryUrlFilterMethod filterMethod) {
        Content content = read(uri, topics, filterMethod);
        return generate(content, template);
    }

    @Override
    public void close() {
        webPageExtractor.close();
    }
}
