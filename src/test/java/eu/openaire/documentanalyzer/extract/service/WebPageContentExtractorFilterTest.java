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

import eu.openaire.documentanalyzer.common.model.HtmlContent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
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

    @Test
    void limitSupplementaryUrls_keepsClosestPathsWhenTooMany() {
        URI request = URI.create("https://example.org/en/data/policies");
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add("https://example.org/en/data/policies/a");
        candidates.add("https://example.org/en/data/policies/b");
        candidates.add("https://example.org/en/data/repository");
        candidates.add("https://example.org/en/about");
        candidates.add("https://example.org/contact");

        for (int i = 0; i < 55; i++) {
            candidates.add("https://example.org/misc/page-" + i);
        }

        Set<String> result = WebPageContentExtractor.limitSupplementaryUrls(request, candidates);

        assertThat(result).hasSize(50);
        assertThat(result).contains("https://example.org/en/data/policies/a");
        assertThat(result).contains("https://example.org/en/data/policies/b");
        assertThat(result).contains("https://example.org/en/data/repository");
        assertThat(result).doesNotContain("https://example.org/misc/page-49");
    }

    @Test
    void limitSupplementaryUrls_preservesOriginalOrderAmongEquallyRelevantUrls() {
        URI request = URI.create("https://example.org/en/data");
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = 0; i < 55; i++) {
            candidates.add("https://example.org/en/data/page-" + i);
        }

        Set<String> result = WebPageContentExtractor.limitSupplementaryUrls(request, candidates);

        assertThat(result).containsExactlyElementsOf(
                List.of(
                        "https://example.org/en/data/page-0",
                        "https://example.org/en/data/page-1",
                        "https://example.org/en/data/page-2",
                        "https://example.org/en/data/page-3",
                        "https://example.org/en/data/page-4",
                        "https://example.org/en/data/page-5",
                        "https://example.org/en/data/page-6",
                        "https://example.org/en/data/page-7",
                        "https://example.org/en/data/page-8",
                        "https://example.org/en/data/page-9",
                        "https://example.org/en/data/page-10",
                        "https://example.org/en/data/page-11",
                        "https://example.org/en/data/page-12",
                        "https://example.org/en/data/page-13",
                        "https://example.org/en/data/page-14",
                        "https://example.org/en/data/page-15",
                        "https://example.org/en/data/page-16",
                        "https://example.org/en/data/page-17",
                        "https://example.org/en/data/page-18",
                        "https://example.org/en/data/page-19",
                        "https://example.org/en/data/page-20",
                        "https://example.org/en/data/page-21",
                        "https://example.org/en/data/page-22",
                        "https://example.org/en/data/page-23",
                        "https://example.org/en/data/page-24",
                        "https://example.org/en/data/page-25",
                        "https://example.org/en/data/page-26",
                        "https://example.org/en/data/page-27",
                        "https://example.org/en/data/page-28",
                        "https://example.org/en/data/page-29",
                        "https://example.org/en/data/page-30",
                        "https://example.org/en/data/page-31",
                        "https://example.org/en/data/page-32",
                        "https://example.org/en/data/page-33",
                        "https://example.org/en/data/page-34",
                        "https://example.org/en/data/page-35",
                        "https://example.org/en/data/page-36",
                        "https://example.org/en/data/page-37",
                        "https://example.org/en/data/page-38",
                        "https://example.org/en/data/page-39",
                        "https://example.org/en/data/page-40",
                        "https://example.org/en/data/page-41",
                        "https://example.org/en/data/page-42",
                        "https://example.org/en/data/page-43",
                        "https://example.org/en/data/page-44",
                        "https://example.org/en/data/page-45",
                        "https://example.org/en/data/page-46",
                        "https://example.org/en/data/page-47",
                        "https://example.org/en/data/page-48",
                        "https://example.org/en/data/page-49"
                )
        );
    }

    @Test
    void extractSameDomainUrls_returnsAbsoluteLinksOnSameHostOnly() {
        HtmlContent content = HtmlContent.of("""
                <html><body>
                <a href="/en/page-a">A</a>
                <a href="https://example.org/en/page-b">B</a>
                <a href="https://other.org/en/page-c">C</a>
                <a href="#section">D</a>
                </body></html>
                """, "text");
        URI request = URI.create("https://example.org/en/home");

        Set<String> result = WebPageContentExtractor.extractSameDomainUrls(content, request);

        assertThat(result).containsExactly(
                "https://example.org/en/page-a",
                "https://example.org/en/page-b",
                "https://example.org/en/home#section"
        );
    }

    @Test
    void simpleSupplementaryUrlFilter_keepsUrlsMatchingTopics() {
        SupplementaryUrlFilter filter = new SimpleSupplementaryUrlFilter();
        URI request = URI.create("https://example.org/en/home");
        Set<String> candidates = urls(
                "https://example.org/about",
                "https://example.org/history-of-the-university",
                "https://example.org/contact"
        );

        Set<String> result = filter.filter(request, candidates, List.of("history", "about"));

        assertThat(result).containsExactly(
                "https://example.org/about",
                "https://example.org/history-of-the-university"
        );
    }

    @Test
    void simpleSupplementaryUrlFilter_fallsBackToOriginalSetWhenNothingMatches() {
        SupplementaryUrlFilter filter = new SimpleSupplementaryUrlFilter();
        URI request = URI.create("https://example.org/en/home");
        Set<String> candidates = urls(
                "https://example.org/contact",
                "https://example.org/news"
        );

        Set<String> result = filter.filter(request, candidates, List.of("history"));

        assertThat(result).isEqualTo(candidates);
    }
}
