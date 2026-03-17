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

package eu.openaire.documentanalyzer.common.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlContentTest {

    private static final String MAIN_URL = "https://example.com";
    private static final String MAIN_HTML_CONTENT = "<p>Main page content</p>";
    private static final String MAIN_TEXT_CONTENT = "Main content";

    private static final String EXTRA_URL = "https://example.com/extra";
    private static final String EXTRA_HTML_CONTENT = "<p>Main page content</p><p>Extra unique section content</p>";
    private static final String EXTRA_TEXT_CONTENT = "Extra content";

    @Test
    void of_setsHtmlAndText() {
        HtmlContent content = HtmlContent.of(MAIN_HTML_CONTENT, MAIN_TEXT_CONTENT);
        assertThat(content.getHtml()).isEqualTo(MAIN_HTML_CONTENT);
        assertThat(content.getText()).isEqualTo(MAIN_TEXT_CONTENT);
    }

    @Test
    void addExtraContent_whenUniqueContent_addsToExtraContent() {
        HtmlContent main = HtmlContent.of(MAIN_URL, MAIN_HTML_CONTENT, MAIN_TEXT_CONTENT);
        HtmlContent extra = HtmlContent.of(EXTRA_URL, EXTRA_HTML_CONTENT, EXTRA_TEXT_CONTENT);

        main.addExtraContent(extra);

        assertThat(main.getExtraContent()).containsKey(EXTRA_URL);
    }

    @Test
    void addExtraContent_whenIdenticalContent_doesNotAddEntry() {
        String html = "<p>Same content</p>";
        HtmlContent main = HtmlContent.of(MAIN_URL, html, "Same");
        HtmlContent extra = HtmlContent.of(EXTRA_URL, html, "Same");

        main.addExtraContent(extra);

        assertThat(main.getExtraContent()).isEmpty();
    }

    @Test
    void toString_withNoExtraContent_usesSimpleFormat() {
        HtmlContent content = HtmlContent.of("<p>Test</p>", "Test");
        String result = content.toString();
        assertThat(result).contains("HTML Content");
        assertThat(result).doesNotContain("SUPPLEMENTARY");
    }

    @Test
    void toString_withExtraContent_usesExtendedFormat() {
        HtmlContent main = HtmlContent.of(MAIN_URL, MAIN_HTML_CONTENT, MAIN_TEXT_CONTENT);
        HtmlContent extra = HtmlContent.of(EXTRA_URL, EXTRA_HTML_CONTENT, EXTRA_TEXT_CONTENT);

        main.addExtraContent(extra);

        String result = main.toString();
        assertThat(result).contains("MAIN HTML Content");
        assertThat(result).contains("SUPPLEMENTARY HTML Content");
    }

    @Test
    void copyConstructor_copiesAllFields() {
        HtmlContent original = HtmlContent.of(MAIN_URL, MAIN_HTML_CONTENT, MAIN_TEXT_CONTENT);
        original.setMetadata("title: Test");

        HtmlContent copy = new HtmlContent(original);

        assertThat(copy.getHtml()).isEqualTo(original.getHtml());
        assertThat(copy.getText()).isEqualTo(original.getText());
        assertThat(copy.getUrl()).isEqualTo(original.getUrl());
        assertThat(copy.getMetadata()).isEqualTo(original.getMetadata());
    }

    @Test
    void copyConstructor_extraContentIsIndependent() {
        HtmlContent original = HtmlContent.of(MAIN_URL, MAIN_HTML_CONTENT, MAIN_TEXT_CONTENT);
        original.getExtraContent().put(EXTRA_URL, EXTRA_HTML_CONTENT);

        HtmlContent copy = new HtmlContent(original);
        copy.getExtraContent().put("https://new.com", "<p>new data</>");

        assertThat(original.getExtraContent()).doesNotContainKey("https://new.com");
    }

    @Test
    void getExtraContent_defaultsToEmptyMap() {
        HtmlContent content = new HtmlContent();
        assertThat(content.getExtraContent()).isNotNull().isEmpty();
    }
}
