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

import javax.net.ssl.SSLHandshakeException;
import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HttpUriReaderTest {

    @Test
    void certificateHostnameFallback_rewritesWwwHostOnSanMismatch() {
        URI request = URI.create("https://www.academyofathens.gr/some/path?x=1#section");
        SSLHandshakeException exception = new SSLHandshakeException(
                "No subject alternative DNS name matching www.academyofathens.gr found."
        );

        Optional<URI> fallback = HttpUriReader.certificateHostnameFallback(request, exception);

        assertThat(fallback).hasValue(URI.create("https://academyofathens.gr/some/path?x=1#section"));
    }

    @Test
    void certificateHostnameFallback_returnsEmptyForNonWwwHost() {
        URI request = URI.create("https://academyofathens.gr/some/path");
        SSLHandshakeException exception = new SSLHandshakeException(
                "No subject alternative DNS name matching academyofathens.gr found."
        );

        Optional<URI> fallback = HttpUriReader.certificateHostnameFallback(request, exception);

        assertThat(fallback).isEmpty();
    }

    @Test
    void certificateHostnameFallback_returnsEmptyForOtherSslErrors() {
        URI request = URI.create("https://www.academyofathens.gr/some/path");
        SSLHandshakeException exception = new SSLHandshakeException("PKIX path building failed");

        Optional<URI> fallback = HttpUriReader.certificateHostnameFallback(request, exception);

        assertThat(fallback).isEmpty();
    }
}
