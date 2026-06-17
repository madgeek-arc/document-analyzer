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

package eu.openaire.documentanalyzer.extract.service;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleSupplementaryUrlFilterTest {

    private final SimpleSupplementaryUrlFilter filter = new SimpleSupplementaryUrlFilter();
    private final URI request = URI.create("https://example.org/en/home");

    private static Set<String> urls(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    @Test
    void method_returnsSimpleFilterMethod() {
        assertThat(filter.method()).isEqualTo(SupplementaryUrlFilterMethod.SIMPLE);
    }

    @Test
    void filter_emptyTopicList_returnsAllUrlsUnchanged() {
        Set<String> candidates = urls("https://example.org/about", "https://example.org/contact");

        Set<String> result = filter.filter(request, candidates, List.of());

        assertThat(result).isSameAs(candidates);
    }

    @Test
    void filter_allTopicsBlank_returnsAllUrlsUnchanged() {
        Set<String> candidates = urls("https://example.org/about", "https://example.org/contact");

        Set<String> result = filter.filter(request, candidates, List.of("  ", ""));

        assertThat(result).isSameAs(candidates);
    }

    @Test
    void filter_fuzzyLevenshteinMatch_returnsMatchingUrl() {
        // "claud" vs topic "cloud": distance=1, maxDistance=1 (both lengths < 6) → match
        // URL does not contain "cloud" as a substring, so substring check fails first
        Set<String> candidates = urls(
                "https://example.org/claud-computing",
                "https://example.org/sports"
        );

        Set<String> result = filter.filter(request, candidates, List.of("cloud"));

        assertThat(result).containsOnly("https://example.org/claud-computing");
    }

    @Test
    void filter_topicContainsUrlToken_returnsMatchingUrl() {
        // URL token "research" is contained in topic "research-and-development"
        // URL does not contain the full topic string as a substring
        Set<String> candidates = urls(
                "https://example.org/research",
                "https://example.org/sports"
        );

        Set<String> result = filter.filter(request, candidates, List.of("research-and-development"));

        assertThat(result).containsOnly("https://example.org/research");
    }
}
