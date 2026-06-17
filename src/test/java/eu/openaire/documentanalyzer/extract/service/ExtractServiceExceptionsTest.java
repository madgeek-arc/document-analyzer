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

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractServiceExceptionsTest {

    @Test
    void robotsDisallowedException_storesMessage() {
        RobotsDisallowedException ex = new RobotsDisallowedException("robots disallowed");
        assertThat(ex.getMessage()).isEqualTo("robots disallowed");
    }

    @Test
    void robotsDisallowedException_isIOException() {
        assertThat(new RobotsDisallowedException("msg")).isInstanceOf(IOException.class);
    }

    @Test
    void rateLimitException_storesMessage() {
        RateLimitException ex = new RateLimitException("rate limited");
        assertThat(ex.getMessage()).isEqualTo("rate limited");
    }

    @Test
    void rateLimitException_isIOException() {
        assertThat(new RateLimitException("msg")).isInstanceOf(IOException.class);
    }

    @Test
    void deferredContentException_storesMessage() {
        DeferredContentException ex = new DeferredContentException("content deferred");
        assertThat(ex.getMessage()).isEqualTo("content deferred");
    }

    @Test
    void deferredContentException_isIOException() {
        assertThat(new DeferredContentException("msg")).isInstanceOf(IOException.class);
    }
}
