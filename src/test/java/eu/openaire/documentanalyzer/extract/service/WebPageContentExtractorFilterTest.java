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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebPageContentExtractorFilterTest {

    // --- helpers -----------------------------------------------------------

    private static Set<String> urls(String... values) {
        return new LinkedHashSet<>(java.util.List.of(values));
    }

    // --- basic path-prefix matching ----------------------------------------

    @Test
    void filterUrlsByPathRelevance_exactPathMatch_returnsMatchingUrls() {
        URI request = URI.create("https://example.org/en/data/policies");
        Set<String> candidates = urls(
                "https://example.org/en/data/policies/overview",
                "https://example.org/en/data/policies/details",
                "https://example.org/about",
                "https://example.org/contact"
        );

        Set<String> result = WebPageContentExtractor.filterUrlsByPathRelevance(request, candidates);

        assertThat(result).containsExactlyInAnyOrder(
                "https://example.org/en/data/policies/overview",
                "https://example.org/en/data/policies/details"
        );
    }

    @Test
    void filterUrlsByPathRelevance_broadsensToParentPathWhenTooFewExactMatches() {
        URI request = URI.create("https://example.org/en/data/policies");
        // only 1 URL matches /en/data/policies — not enough, should broaden to /en/data
        Set<String> candidates = urls(
                "https://example.org/en/data/policies/overview",
                "https://example.org/en/data/repository",
                "https://example.org/about"
        );

        Set<String> result = WebPageContentExtractor.filterUrlsByPathRelevance(request, candidates);

        assertThat(result).containsExactlyInAnyOrder(
                "https://example.org/en/data/policies/overview",
                "https://example.org/en/data/repository"
        );
        assertThat(result).doesNotContain("https://example.org/about");
    }

    @Test
    void filterUrlsByPathRelevance_noMatchAtAnyLevel_returnsAllUrls() {
        URI request = URI.create("https://example.org/en/data/policies");
        Set<String> candidates = urls(
                "https://example.org/fr/something",
                "https://example.org/de/other"
        );

        Set<String> result = WebPageContentExtractor.filterUrlsByPathRelevance(request, candidates);

        assertThat(result).isEqualTo(candidates);
    }

    // --- edge cases --------------------------------------------------------

    @Test
    void filterUrlsByPathRelevance_rootPath_returnsAllUrls() {
        URI request = URI.create("https://example.org/");
        Set<String> candidates = urls(
                "https://example.org/about",
                "https://example.org/contact"
        );

        Set<String> result = WebPageContentExtractor.filterUrlsByPathRelevance(request, candidates);

        assertThat(result).isEqualTo(candidates);
    }

    @Test
    void filterUrlsByPathRelevance_emptyPath_returnsAllUrls() {
        URI request = URI.create("https://example.org");
        Set<String> candidates = urls(
                "https://example.org/about",
                "https://example.org/contact"
        );

        Set<String> result = WebPageContentExtractor.filterUrlsByPathRelevance(request, candidates);

        assertThat(result).isEqualTo(candidates);
    }

    @Test
    void filterUrlsByPathRelevance_queryStringInRequest_ignoredForMatching() {
        URI request = URI.create("https://example.org/en/data/policies?filter=x");
        Set<String> candidates = urls(
                "https://example.org/en/data/policies/overview",
                "https://example.org/en/data/policies/details",
                "https://example.org/about"
        );

        Set<String> result = WebPageContentExtractor.filterUrlsByPathRelevance(request, candidates);

        assertThat(result).containsExactlyInAnyOrder(
                "https://example.org/en/data/policies/overview",
                "https://example.org/en/data/policies/details"
        );
    }

    @Test
    void filterUrlsByPathRelevance_emptyUrlSet_returnsEmpty() {
        URI request = URI.create("https://example.org/en/data");
        Set<String> result = WebPageContentExtractor.filterUrlsByPathRelevance(request, new LinkedHashSet<>());
        assertThat(result).isEmpty();
    }

    @Test
    void filterUrlsByPathRelevance_preservesInsertionOrder() {
        URI request = URI.create("https://example.org/en/data");
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add("https://example.org/en/data/first");
        candidates.add("https://example.org/en/data/second");
        candidates.add("https://example.org/en/data/third");

        Set<String> result = WebPageContentExtractor.filterUrlsByPathRelevance(request, candidates);

        assertThat(result).containsExactly(
                "https://example.org/en/data/first",
                "https://example.org/en/data/second",
                "https://example.org/en/data/third"
        );
    }
}
