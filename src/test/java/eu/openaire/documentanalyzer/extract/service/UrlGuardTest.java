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

import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlGuardTest {

    private final UrlGuard guard = new UrlGuard(false);

    @Test
    void check_rejectsNonHttpSchemes() {
        assertThatThrownBy(() -> guard.check(URI.create("file:///etc/passwd"))).isInstanceOf(BlockedUrlException.class);
        assertThatThrownBy(() -> guard.check(URI.create("ftp://93.184.216.34/file"))).isInstanceOf(BlockedUrlException.class);
    }

    @Test
    void check_rejectsNonPublicAddresses() {
        for (String url : new String[]{
                "http://127.0.0.1/", "http://localhost:8080/", "http://10.1.2.3/", "http://172.16.0.1/",
                "http://192.168.1.1/", "http://169.254.169.254/latest/meta-data/", "http://0.0.0.0/",
                "http://100.64.0.1/", "http://[::1]/", "http://[fd00::1]/", "http://224.0.0.1/"}) {
            assertThatThrownBy(() -> guard.check(URI.create(url)))
                    .as(url)
                    .isInstanceOf(BlockedUrlException.class);
        }
    }

    @Test
    void check_allowsPublicAddress() {
        assertThatCode(() -> guard.check(URI.create("https://93.184.216.34/page"))).doesNotThrowAnyException();
    }

    @Test
    void check_allowPrivateAddresses_stillRequiresHttp() {
        UrlGuard permissive = new UrlGuard(true);
        assertThatCode(() -> permissive.check(URI.create("http://127.0.0.1/"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> permissive.check(URI.create("file:///etc/passwd"))).isInstanceOf(BlockedUrlException.class);
    }

    @Test
    void isAllowedRequest_ignoresNonNetworkSchemes() {
        assertThat(guard.isAllowedRequest("data:image/png;base64,AAAA")).isTrue();
        assertThat(guard.isAllowedRequest("http://127.0.0.1/admin")).isFalse();
        assertThat(guard.isAllowedRequest("https://93.184.216.34/")).isTrue();
    }

    @Test
    void isNonPublic_classifiesAddresses() throws Exception {
        assertThat(UrlGuard.isNonPublic(InetAddress.getByName("100.127.255.255"))).isTrue();
        assertThat(UrlGuard.isNonPublic(InetAddress.getByName("100.128.0.1"))).isFalse();
        assertThat(UrlGuard.isNonPublic(InetAddress.getByName("2001:db8::1"))).isFalse();
    }
}
