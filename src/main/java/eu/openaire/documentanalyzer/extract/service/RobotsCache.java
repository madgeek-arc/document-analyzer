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

import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches and caches {@code robots.txt} rules per origin (scheme + host + port).
 * Uses {@link SimpleRobotRulesParser} from crawler-commons to parse directives.
 * If a site's {@code robots.txt} cannot be fetched, all URLs for that origin are
 * assumed to be allowed (permissive fallback per RFC 9309 §2.3.1).
 */
public class RobotsCache {

    private static final Logger logger = LoggerFactory.getLogger(RobotsCache.class);

    /** Bot name used when matching {@code User-agent} lines in {@code robots.txt}. */
    static final String BOT_NAME = "DocumentAnalyzerBot";

    private final Map<String, BaseRobotRules> cache = new ConcurrentHashMap<>();
    private final HttpUriReader reader;

    /**
     * @param reader used to fetch {@code robots.txt} files; should have no artificial delay
     *               since robots.txt is fetched only once per origin and then cached
     */
    public RobotsCache(HttpUriReader reader) {
        this.reader = reader;
    }

    /**
     * Returns {@code true} if {@code robots.txt} for the given URL's origin allows
     * {@value BOT_NAME} to access it. Falls back to {@code true} on any error.
     *
     * @param url the URL to check
     * @return whether crawling is allowed
     */
    public boolean isAllowed(String url) {
        try {
            URI uri = URI.create(url);
            int port = uri.getPort();
            String origin = uri.getScheme() + "://" + uri.getHost()
                    + (port != -1 ? ":" + port : "");
            BaseRobotRules rules = cache.computeIfAbsent(origin, this::fetchRules);
            return rules.isAllowed(url);
        } catch (Exception e) {
            logger.debug("Could not check robots.txt for {}, assuming allowed", url, e);
            return true;
        }
    }

    private BaseRobotRules fetchRules(String origin) {
        URI robotsUri = URI.create(origin + "/robots.txt");
        try {
            UriReader.Data data = reader.read(robotsUri);
            SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
            BaseRobotRules rules = parser.parseContent(
                    robotsUri.toString(), data.data(), "text/plain", List.of(BOT_NAME));
            logger.debug("Loaded robots.txt from {}", robotsUri);
            return rules;
        } catch (Exception e) {
            logger.debug("Could not fetch robots.txt from {}, assuming all allowed: {}", robotsUri, e.getMessage());
            return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
        }
    }
}
