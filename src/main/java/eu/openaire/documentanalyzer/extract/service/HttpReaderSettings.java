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

import java.time.Duration;

/**
 * Settings shared by the HTTP readers of the extractors.
 *
 * @param requestDelayMs       delay before every request, to reduce the risk of rate limiting
 * @param connectTimeout       TCP/TLS connect timeout
 * @param requestTimeout       timeout for receiving the response headers of a single request
 * @param trustAllCertificates accept any server certificate chain (hostname verification still applies)
 * @param allowPrivateAddresses allow URLs that resolve to loopback, private or other non-public addresses
 */
public record HttpReaderSettings(long requestDelayMs,
                                 Duration connectTimeout,
                                 Duration requestTimeout,
                                 boolean trustAllCertificates,
                                 boolean allowPrivateAddresses) {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    public static HttpReaderSettings defaults() {
        return new HttpReaderSettings(0, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT, false, false);
    }

    public HttpReaderSettings withRequestDelayMs(long requestDelayMs) {
        return new HttpReaderSettings(requestDelayMs, connectTimeout, requestTimeout, trustAllCertificates,
                allowPrivateAddresses);
    }
}
