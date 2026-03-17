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

package eu.openaire.documentanalyzer.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiffUtilsTest {

    @Test
    void extractUniquePart_identicalTexts_returnsEmpty() {
        String text = "line one\nline two\nline three";
        String result = DiffUtils.extractUniquePart(text, text);
        assertThat(result).isEmpty();
    }

    @Test
    void extractUniquePart_secondaryHasAdditionalLines_returnsInsertedLines() {
        String main = "line one\nline two";
        String secondary = "line one\nline two\nline three";
        String result = DiffUtils.extractUniquePart(main, secondary);
        assertThat(result).isEqualTo("line three");
    }

    @Test
    void extractUniquePart_secondaryHasChangedLine_returnsChangedLine() {
        String main = "line one\nline two";
        String secondary = "line one\nline modified";
        String result = DiffUtils.extractUniquePart(main, secondary);
        assertThat(result).isEqualTo("line modified");
    }

    @Test
    void extractUniquePart_mainIsEmpty_returnsEntireSecondary() {
        String main = "";
        String secondary = "new content\nmore content";
        String result = DiffUtils.extractUniquePart(main, secondary);
        assertThat(result).isEqualTo("new content\nmore content");
    }

    @Test
    void extractUniquePart_secondaryIsEmpty_returnsEmpty() {
        String main = "line one\nline two";
        String secondary = "";
        String result = DiffUtils.extractUniquePart(main, secondary);
        assertThat(result).isEmpty();
    }

    @Test
    void extractUniquePart_multipleChanges_returnsAllChangedLines() {
        String main = "line one\nline two\nline three";
        String secondary = "line one\nchanged two\nchanged three";
        String result = DiffUtils.extractUniquePart(main, secondary);
        assertThat(result).contains("changed two").contains("changed three");
    }
}
