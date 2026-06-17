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
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openaire.documentanalyzer.common.model.Content;
import eu.openaire.documentanalyzer.common.model.HtmlContent;
import eu.openaire.documentanalyzer.common.model.PdfContent;
import eu.openaire.documentanalyzer.enrich.service.DocumentContentProcessor;
import eu.openaire.documentanalyzer.extract.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentAnalyzerServiceTest {

    @Mock WebPageContentExtractor webPageExtractor;
    @Mock PdfContentExtractor pdfExtractor;
    @Mock HttpUriReader contentReader;
    @Mock DocumentContentProcessor contentProcessor;

    private DocumentAnalyzerService service;

    @BeforeEach
    void setUp() {
        service = new DocumentAnalyzerService(webPageExtractor, pdfExtractor, contentReader, contentProcessor);
    }

    @Test
    void read_pdfUri_routesToPdfExtractor() throws IOException {
        URI uri = URI.create("https://example.com/paper.pdf");
        byte[] bytes = {1, 2, 3};
        UriReader.Data data = new UriReader.Data(uri, bytes);
        PdfContent expected = new PdfContent();

        when(contentReader.read(uri)).thenReturn(data);
        when(pdfExtractor.extract(bytes)).thenReturn(expected);

        Content result = service.read(uri);

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(webPageExtractor);
    }

    @Test
    void read_htmlUri_routesToWebPageExtractor() throws IOException {
        URI uri = URI.create("https://example.com/page");
        UriReader.Data data = new UriReader.Data(uri, new byte[0]);
        HtmlContent expected = new HtmlContent();

        when(contentReader.read(uri)).thenReturn(data);
        when(webPageExtractor.extractWholeSite(any(), any(), any())).thenReturn(expected);

        Content result = service.read(uri);

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(pdfExtractor);
    }

    @Test
    void read_passesTopicsAndFilterMethod() throws IOException {
        URI uri = URI.create("https://example.com/page");
        UriReader.Data data = new UriReader.Data(uri, new byte[0]);
        List<String> topics = List.of("ai", "research");

        when(contentReader.read(uri)).thenReturn(data);
        when(webPageExtractor.extractWholeSite(uri, topics, SupplementaryUrlFilterMethod.SIMPLE))
                .thenReturn(new HtmlContent());

        service.read(uri, topics, SupplementaryUrlFilterMethod.SIMPLE);

        verify(webPageExtractor).extractWholeSite(uri, topics, SupplementaryUrlFilterMethod.SIMPLE);
    }

    @Test
    void read_deferredContentException_fallsBackToExtractFromUrl() throws IOException {
        URI uri = URI.create("https://example.com/page");
        HtmlContent expected = new HtmlContent();

        when(contentReader.read(uri)).thenThrow(new DeferredContentException("deferred"));
        when(webPageExtractor.extractFromUrl(uri.toString())).thenReturn(expected);

        Content result = service.read(uri);

        assertThat(result).isSameAs(expected);
        verify(webPageExtractor).extractFromUrl(uri.toString());
    }

    @Test
    void read_deferredFallback_ioException_wrapsAsRuntimeException() throws IOException {
        URI uri = URI.create("https://example.com/page");

        when(contentReader.read(uri)).thenThrow(new DeferredContentException("deferred"));
        when(webPageExtractor.extractFromUrl(anyString())).thenThrow(new IOException("fallback failed"));

        assertThatThrownBy(() -> service.read(uri))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void read_ioException_wrapsAsRuntimeException() throws IOException {
        URI uri = URI.create("https://example.com/page");

        when(contentReader.read(uri)).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> service.read(uri))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void generate_content_delegatesToProcessor() throws Exception {
        Content content = new HtmlContent();
        JsonNode template = new ObjectMapper().readTree("{\"title\":\"\"}");
        JsonNode expected = new ObjectMapper().readTree("{\"title\":\"Result\"}");

        when(contentProcessor.generate(template, content)).thenReturn(expected);

        JsonNode result = service.generate(content, template);

        assertThat(result).isSameAs(expected);
        verify(contentProcessor).generate(template, content);
    }

    @Test
    void close_delegatesToWebPageExtractor() {
        service.close();
        verify(webPageExtractor).close();
    }
}
