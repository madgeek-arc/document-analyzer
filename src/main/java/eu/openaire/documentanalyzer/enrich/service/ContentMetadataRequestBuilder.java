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

package eu.openaire.documentanalyzer.enrich.service;

import com.fasterxml.jackson.databind.JsonNode;
import eu.openaire.documentanalyzer.common.model.Content;
import eu.openaire.documentanalyzer.common.model.HtmlContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContentMetadataRequestBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ContentMetadataRequestBuilder.class);

    public <T extends Content> String request(JsonNode json, T content) {
        return switch (content) {
            case HtmlContent html -> request(json, html);
            default -> request(json, content.getText());
        };
    }

    public <T extends Content> String request(T content) {
        return switch (content) {
            case HtmlContent html -> request(html);
            default -> request(content.getText());
        };
    }

    public String request(String documentContent) {
        return """
               Extract the MOST INFORMATIVE PARAGRAPHS of the provided document and fill up a JSON array '[]' with their content.
               
               === Document Content ===
                %s
                ========================
                
                You will output only a valid JSON array in plain text, without any formatting.
               """.formatted(documentContent);
    }

    private String request(HtmlContent documentContent) {
        String request = request(documentContent.toString());
        return """
                %s
                
                If the provided HTML Document contains a not found message,
                (e.g. "We're sorry, but the page you were looking for doesn't exist.", "404", "Not Found")
                then return an empty JSON array '[]'
                
                Use only the main content of the HTML, discard top menu and footer.
                """.formatted(request);
    }

    public String request(JsonNode json, String documentContent) {
        return """
                Fill up the following JSON template with the content extracted from the provided document.
                If a field is not available, return an empty string or empty list.
                
                === JSON Template ===
                %s
                =====================
                
                === Document Content ===
                %s
                ========================
                
                You will output only a valid JSON object in plain text, without any formatting.
                """.formatted(
                json.toPrettyString(), documentContent
        );
    }

    private String request(JsonNode json, HtmlContent documentContent) {
        String request = request(json, documentContent.toString());
        return """
                %s
                
                If the provided HTML Document contains a not found message,
                (e.g. "We're sorry, but the page you were looking for doesn't exist.", "404", "Not Found")
                then return an empty JSON '{}'
                
                Use only the main content of the HTML, discard top menu and footer.
                """.formatted(request);
    }
}
