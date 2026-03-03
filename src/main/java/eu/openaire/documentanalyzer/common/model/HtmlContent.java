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

package eu.openaire.documentanalyzer.common.model;

import java.util.LinkedHashMap;
import java.util.Map;

import static eu.openaire.documentanalyzer.utils.DiffUtils.extractUniquePart;

public class HtmlContent extends Content {

    private String url;
    private String html;
    private Map<String, String> extraContent = new LinkedHashMap<>();

    public HtmlContent() {
    }

    public HtmlContent(HtmlContent content) {
        super(content);
        this.url = content.getUrl();
        this.html = content.getHtml();
        this.extraContent = new LinkedHashMap<>(content.getExtraContent());
    }

    public static HtmlContent of(String url, String html, String text) {
        HtmlContent content = new HtmlContent();
        content.setUrl(url);
        content.setHtml(html);
        content.setText(text);
        return content;
    }

    public static HtmlContent of(String html, String text) {
        HtmlContent content = new HtmlContent();
        content.setHtml(html);
        content.setText(text);
        return content;
    }

    public void addExtraContent(HtmlContent extra) {
        if (extraContent == null) {
            this.extraContent = new LinkedHashMap<>();
        }
        String uniqueData = extractUniquePart(this.getHtml(), extra.getHtml());

        if (!uniqueData.isBlank()) {
            this.getExtraContent().put(extra.getUrl(), uniqueData);
        }
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public Map<String, String> getExtraContent() {
        return extraContent;
    }

    public void setExtraContent(Map<String, String> extraContent) {
        this.extraContent = extraContent;
    }

    @Override
    public String toString() {
        if (extraContent != null && !extraContent.isEmpty()) {
            return extendedToString();
        } else {
            return simpleToString();
        }
    }

    private String simpleToString() {
        return """
                === HTML Content ===
                %s
                ====================
                """.formatted(
                this.html
        );
    }

    private String extendedToString() {
        StringBuilder supplementaryContent = new StringBuilder();
        for (Map.Entry<String, String> entry : extraContent.entrySet()) {
            supplementaryContent.append("\n\nSite: ").append(entry.getKey());
            supplementaryContent.append("\nHTML:");
            supplementaryContent.append("\n----------------------------------\n");
            supplementaryContent.append(entry.getValue());
            supplementaryContent.append("\n----------------------------------");
        }

        return """
                === MAIN HTML Content ===
                SITE: %s
                HTML:
                -------------------------
                %s
                -------------------------
                =========================
                
                === SUPPLEMENTARY HTML Content ===
                %s
                ==================================
                """.formatted(
                this.url, this.html, supplementaryContent
        );
    }
}
