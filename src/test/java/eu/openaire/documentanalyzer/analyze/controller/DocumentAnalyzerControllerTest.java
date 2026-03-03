package eu.openaire.documentanalyzer.analyze.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openaire.documentanalyzer.analyze.service.DocumentAnalyzerService;
import eu.openaire.documentanalyzer.common.model.HtmlContent;
import eu.openaire.documentanalyzer.common.model.PdfContent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
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
        when(documentAnalyzerService.read(any(URI.class))).thenReturn(content);

        mockMvc.perform(post("/v1/documents/extract")
                        .param("url", "https://example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void extractContent_withPdfUrl_returnsPdfContent() throws Exception {
        PdfContent pdfContent = PdfContent.of("Author: Test Author", "PDF body text");
        when(documentAnalyzerService.read(any(URI.class))).thenReturn(pdfContent);

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
        when(documentAnalyzerService.generate(any(URI.class), any(JsonNode.class))).thenReturn(mockResponse);

        String template = "{\"title\": null, \"abstract\": null}";

        mockMvc.perform(post("/v1/documents/enrich")
                        .param("url", "https://example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title").value("Test Title"))
                .andExpect(jsonPath("$.abstract").value("Test abstract"));
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
