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

package eu.openaire.documentanalyzer.analyze.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openaire.documentanalyzer.analyze.service.DocumentAnalyzerService;
import eu.openaire.documentanalyzer.common.model.HtmlContent;
import eu.openaire.documentanalyzer.common.model.PdfContent;
import eu.openaire.documentanalyzer.extract.service.SupplementaryUrlFilterMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentAnalyzerController.class)
@ActiveProfiles("test")
class DocumentAnalyzerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentAnalyzerService documentAnalyzerService;

    @Test
    void extractContent_withHtmlUrl_returnsOkWithJson() throws Exception {
        HtmlContent content = HtmlContent.of("<p>Test</p>", "Test content");
        content.setUrl("https://example.com");
        when(documentAnalyzerService.read(any(URI.class), any(List.class), any(SupplementaryUrlFilterMethod.class))).thenReturn(content);

        mockMvc.perform(post("/v1/documents/extract")
                        .param("url", "https://example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(documentAnalyzerService).read(
                URI.create("https://example.com"),
                List.of(),
                SupplementaryUrlFilterMethod.SIMPLE
        );
    }

    @Test
    void extractContent_withPdfUrl_returnsPdfContent() throws Exception {
        PdfContent pdfContent = PdfContent.of("Author: Test Author", "PDF body text");
        when(documentAnalyzerService.read(any(URI.class), any(List.class), any(SupplementaryUrlFilterMethod.class))).thenReturn(pdfContent);

        mockMvc.perform(post("/v1/documents/extract")
                        .param("url", "https://example.com/document.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.text").value("PDF body text"));
    }

    @Test
    void extractContent_missingUrlParam_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/documents/extract"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrichDocument_withValidUrlAndTemplate_returnsEnrichedJson() throws Exception {
        JsonNode mockResponse = objectMapper.readTree("{\"title\": \"Test Title\", \"abstract\": \"Test abstract\"}");
        when(documentAnalyzerService.generate(any(URI.class), any(JsonNode.class), any(List.class), any(SupplementaryUrlFilterMethod.class)))
                .thenReturn(mockResponse);

        String template = "{\"title\": null, \"abstract\": null}";

        mockMvc.perform(post("/v1/documents/enrich")
                        .param("url", "https://example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title").value("Test Title"))
                .andExpect(jsonPath("$.abstract").value("Test abstract"));

        verify(documentAnalyzerService).generate(
                eq(URI.create("https://example.com")),
                any(JsonNode.class),
                eq(List.of()),
                eq(SupplementaryUrlFilterMethod.SIMPLE)
        );
    }

    @Test
    void extractContent_withTopicsAndFilterMethod_propagatesOptions() throws Exception {
        HtmlContent content = HtmlContent.of("<p>Test</p>", "Test content");
        content.setUrl("https://example.com");
        when(documentAnalyzerService.read(any(URI.class), any(List.class), any(SupplementaryUrlFilterMethod.class))).thenReturn(content);

        mockMvc.perform(post("/v1/documents/extract")
                        .param("url", "https://example.com")
                        .param("topics", "description", "history")
                        .param("filterMethod", "SIMPLE"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(documentAnalyzerService).read(
                URI.create("https://example.com"),
                List.of("description", "history"),
                SupplementaryUrlFilterMethod.SIMPLE
        );
    }

    @Test
    void enrichDocument_withTopicsAndFilterMethod_propagatesOptions() throws Exception {
        JsonNode mockResponse = objectMapper.readTree("{\"title\": \"Test Title\"}");
        when(documentAnalyzerService.generate(any(URI.class), any(JsonNode.class), any(List.class), any(SupplementaryUrlFilterMethod.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/v1/documents/enrich")
                        .param("url", "https://example.com")
                        .param("topics", "about", "history")
                        .param("filterMethod", "SIMPLE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": null}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title").value("Test Title"));

        verify(documentAnalyzerService).generate(
                eq(URI.create("https://example.com")),
                any(JsonNode.class),
                eq(List.of("about", "history")),
                eq(SupplementaryUrlFilterMethod.SIMPLE)
        );
    }

    @Test
    void enrichDocument_missingUrlParam_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/documents/enrich")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrichDocument_missingRequestBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/documents/enrich")
                        .param("url", "https://example.com"))
                .andExpect(status().isBadRequest());
    }
}
