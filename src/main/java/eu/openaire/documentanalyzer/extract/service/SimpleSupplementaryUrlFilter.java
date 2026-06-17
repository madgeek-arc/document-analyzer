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

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class SimpleSupplementaryUrlFilter implements SupplementaryUrlFilter {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[^a-z0-9]+");

    @Override
    public SupplementaryUrlFilterMethod method() {
        return SupplementaryUrlFilterMethod.SIMPLE;
    }

    @Override
    public Set<String> filter(URI requestUri, Set<String> urls, List<String> topics) {
        List<String> normalizedTopics = topics.stream()
                .map(SimpleSupplementaryUrlFilter::normalize)
                .filter(topic -> !topic.isBlank())
                .distinct()
                .toList();
        if (normalizedTopics.isEmpty()) {
            return urls;
        }

        LinkedHashSet<String> matches = new LinkedHashSet<>();
        for (String url : urls) {
            String normalizedUrl = normalize(url);
            Set<String> urlTokens = tokenize(normalizedUrl);
            if (normalizedTopics.stream().anyMatch(topic -> matchesTopic(topic, normalizedUrl, urlTokens))) {
                matches.add(url);
            }
        }
        return matches.isEmpty() ? urls : matches;
    }

    private static boolean matchesTopic(String topic, String normalizedUrl, Set<String> urlTokens) {
        if (normalizedUrl.contains(topic)) {
            return true;
        }
        for (String token : urlTokens) {
            if (token.equals(topic) || token.contains(topic) || topic.contains(token)) {
                return true;
            }
            int maxDistance = token.length() >= 6 && topic.length() >= 6 ? 2 : 1;
            if (levenshteinDistance(token, topic) <= maxDistance) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> tokenize(String value) {
        return Arrays.stream(SPLIT_PATTERN.split(value))
                .filter(token -> !token.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static int levenshteinDistance(String left, String right) {
        int[][] dp = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[left.length()][right.length()];
    }
}
