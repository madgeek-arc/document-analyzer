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

class PdfContentTest {

    @Test
    void copyConstructor_copiesFieldsFromContent() {
        Content source = new Content();
        source.setText("PDF body text");
        source.setMetadata("author: Alice");

        PdfContent copy = new PdfContent(source);

        assertThat(copy.getText()).isEqualTo("PDF body text");
        assertThat(copy.getMetadata()).isEqualTo("author: Alice");
    }

    @Test
    void toString_containsMetadataAndText() {
        PdfContent content = PdfContent.of("author: Bob", "Document body");

        String result = content.toString();

        assertThat(result).contains("PDF Metadata");
        assertThat(result).contains("author: Bob");
        assertThat(result).contains("PDF Content");
        assertThat(result).contains("Document body");
    }
}
