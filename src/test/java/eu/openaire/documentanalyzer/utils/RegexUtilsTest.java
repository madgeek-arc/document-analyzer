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

class RegexUtilsTest {

    @Test
    void countOccurrences_multipleMatches_returnsCorrectCount() {
        assertThat(RegexUtils.countOccurrences("hello world hello", "hello")).isEqualTo(2);
    }

    @Test
    void countOccurrences_noMatches_returnsZero() {
        assertThat(RegexUtils.countOccurrences("hello world", "xyz")).isZero();
    }

    @Test
    void countOccurrences_regexPattern_returnsCorrectCount() {
        assertThat(RegexUtils.countOccurrences("cat bat rat", "[cbr]at")).isEqualTo(3);
    }

    @Test
    void countOccurrences_emptyInput_returnsZero() {
        assertThat(RegexUtils.countOccurrences("", "pattern")).isZero();
    }

    @Test
    void countOccurrences_singleMatch_returnsOne() {
        assertThat(RegexUtils.countOccurrences("one two three", "two")).isEqualTo(1);
    }

    @Test
    void countOccurrences_overlappingMatches_countsNonOverlapping() {
        // "aa" in "aaaa" matches at positions 0 and 2 (non-overlapping)
        assertThat(RegexUtils.countOccurrences("aaaa", "aa")).isEqualTo(2);
    }
}
