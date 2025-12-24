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

public class HtmlContent extends Content {

    private String html;

    public HtmlContent() {
    }

    public static HtmlContent of(String html, String text) {
        HtmlContent content = new HtmlContent();
        content.setHtml(html);
        content.setText(text);
        return content;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    @Override
    public String toString() {
        return """
                === HTML Content ===
                %s
                ====================
                """.formatted(
                this.html
        );
    }
}
