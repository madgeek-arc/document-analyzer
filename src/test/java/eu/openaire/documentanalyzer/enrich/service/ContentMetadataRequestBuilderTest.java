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

package eu.openaire.documentanalyzer.enrich.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openaire.documentanalyzer.common.model.Content;
import eu.openaire.documentanalyzer.common.model.HtmlContent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentMetadataRequestBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ContentMetadataRequestBuilder builder = new ContentMetadataRequestBuilder();

    @Test
    void request_string_embedsDocumentContent() {
        String result = builder.request("some document text");
        assertThat(result).contains("some document text");
    }

    @Test
    void request_string_mentionsJsonArray() {
        String result = builder.request("any content");
        assertThat(result).contains("[]");
    }

    @Test
    void request_jsonString_embedsTemplateAndContent() throws Exception {
        JsonNode template = MAPPER.readTree("{\"title\":\"\",\"description\":\"\"}");
        String result = builder.request(template, "document body");
        assertThat(result).contains("document body");
        assertThat(result).contains("title");
    }

    @Test
    void request_jsonString_mentionsJsonObject() throws Exception {
        JsonNode template = MAPPER.readTree("{\"title\":\"\"}");
        String result = builder.request(template, "content");
        assertThat(result).contains("JSON object");
    }

    @Test
    void request_plainContent_delegatesToStringMethod() {
        Content content = new Content();
        content.setText("plain content text");
        String result = builder.request(content);
        assertThat(result).contains("plain content text");
        assertThat(result).contains("[]");
    }

    @Test
    void request_htmlContent_addsNotFoundInstructions() {
        HtmlContent html = HtmlContent.of("<html><body>page body</body></html>", "page body");
        String result = builder.request(html);
        assertThat(result).contains("404");
        assertThat(result).containsIgnoringCase("not found");
    }

    @Test
    void request_jsonPlainContent_delegatesToJsonStringMethod() throws Exception {
        JsonNode template = MAPPER.readTree("{\"name\":\"\"}");
        Content content = new Content();
        content.setText("plain text body");
        String result = builder.request(template, content);
        assertThat(result).contains("plain text body");
        assertThat(result).contains("name");
    }

    @Test
    void request_jsonHtmlContent_addsEmptyObjectInstruction() throws Exception {
        JsonNode template = MAPPER.readTree("{\"title\":\"\"}");
        HtmlContent html = HtmlContent.of("<html><body>content</body></html>", "content");
        String result = builder.request(template, html);
        assertThat(result).contains("{}");
        assertThat(result).containsIgnoringCase("not found");
    }
}
