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

class ContentTest {

    @Test
    void setText_stripsLeadingAndTrailingWhitespace() {
        Content content = new Content();
        content.setText("  hello world  ");
        assertThat(content.getText()).isEqualTo("hello world");
    }

    @Test
    void setText_stripsNewlines() {
        Content content = new Content();
        content.setText("\n\nsome text\n\n");
        assertThat(content.getText()).isEqualTo("some text");
    }

    @Test
    void setText_withNoWhitespace_returnsUnchanged() {
        Content content = new Content();
        content.setText("clean text");
        assertThat(content.getText()).isEqualTo("clean text");
    }

    @Test
    void getMetadata_afterSet_returnsCorrectValue() {
        Content content = new Content();
        content.setMetadata("key: value");
        assertThat(content.getMetadata()).isEqualTo("key: value");
    }

    @Test
    void toString_includesMetadataAndText() {
        Content content = new Content();
        content.setMetadata("author: test");
        content.setText("body text");
        String result = content.toString();
        assertThat(result).contains("author: test").contains("body text");
    }

    @Test
    void copyConstructor_copiesFields() {
        Content original = new Content();
        original.setMetadata("meta");
        original.setText("text");
        Content copy = new Content(original);
        assertThat(copy.getMetadata()).isEqualTo("meta");
        assertThat(copy.getText()).isEqualTo("text");
    }

    @Test
    void defaultConstructor_fieldsAreNull() {
        Content content = new Content();
        assertThat(content.getMetadata()).isNull();
        assertThat(content.getText()).isNull();
    }
}
