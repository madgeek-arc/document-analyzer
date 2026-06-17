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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RobotsCacheTest {

    @Mock
    HttpUriReader reader;

    private RobotsCache cache;

    @BeforeEach
    void setUp() {
        cache = new RobotsCache(reader);
    }

    @Test
    void isAllowed_allowsUrl_whenRobotsTxtPermitsAll() throws IOException {
        String robotsTxt = "User-agent: *\nAllow: /\n";
        stubRobotsTxt("https://example.com", robotsTxt);

        assertThat(cache.isAllowed("https://example.com/page")).isTrue();
    }

    @Test
    void isAllowed_disallowsUrl_whenRobotsTxtDisallowsPath() throws IOException {
        String robotsTxt = "User-agent: *\nDisallow: /private/\n";
        stubRobotsTxt("https://example.com", robotsTxt);

        assertThat(cache.isAllowed("https://example.com/private/secret")).isFalse();
    }

    @Test
    void isAllowed_fetchesRobotsTxtOnlyOnce_forSameOrigin() throws IOException {
        String robotsTxt = "User-agent: *\nAllow: /\n";
        stubRobotsTxt("https://example.com", robotsTxt);

        cache.isAllowed("https://example.com/page1");
        cache.isAllowed("https://example.com/page2");

        verify(reader, times(1)).read(any(URI.class));
    }

    @Test
    void isAllowed_returnsTrue_whenRobotsTxtFetchFails() throws IOException {
        when(reader.read(any(URI.class))).thenThrow(new IOException("connection refused"));

        assertThat(cache.isAllowed("https://example.com/page")).isTrue();
    }

    @Test
    void isAllowed_returnsTrue_whenUrlIsInvalid() {
        assertThat(cache.isAllowed("not a valid url")).isTrue();
    }

    @Test
    void isAllowed_includesPortInOrigin_whenNonDefaultPortPresent() throws IOException {
        String robotsTxt = "User-agent: *\nAllow: /\n";
        stubRobotsTxt("https://example.com:8080", robotsTxt);

        cache.isAllowed("https://example.com:8080/page");

        verify(reader).read(URI.create("https://example.com:8080/robots.txt"));
    }

    private void stubRobotsTxt(String origin, String content) throws IOException {
        URI robotsUri = URI.create(origin + "/robots.txt");
        when(reader.read(robotsUri))
                .thenReturn(new UriReader.Data(robotsUri, content.getBytes()));
    }
}
