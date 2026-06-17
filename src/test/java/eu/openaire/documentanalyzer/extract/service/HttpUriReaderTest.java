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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLHandshakeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpUriReaderTest {

    @Mock
    HttpClient httpClient;

    @SuppressWarnings("unchecked")
    @Mock
    HttpResponse<InputStream> response;

    private HttpUriReader reader;

    @BeforeEach
    void setUp() throws Exception {
        reader = new HttpUriReader(httpClient, 0);
    }

    // ── certificateHostnameFallback ───────────────────────────────────────────

    @Test
    void certificateHostnameFallback_rewritesWwwHostOnSanMismatch() {
        URI request = URI.create("https://www.example.com/some/path?x=1#section");
        SSLHandshakeException exception = new SSLHandshakeException(
                "No subject alternative DNS name matching www.example.com found."
        );

        Optional<URI> fallback = HttpUriReader.certificateHostnameFallback(request, exception);

        assertThat(fallback).hasValue(URI.create("https://example.com/some/path?x=1#section"));
    }

    @Test
    void certificateHostnameFallback_returnsEmptyForNonWwwHost() {
        URI request = URI.create("https://example.com/some/path");
        SSLHandshakeException exception = new SSLHandshakeException(
                "No subject alternative DNS name matching example.com found."
        );

        Optional<URI> fallback = HttpUriReader.certificateHostnameFallback(request, exception);

        assertThat(fallback).isEmpty();
    }

    @Test
    void certificateHostnameFallback_returnsEmptyForOtherSslErrors() {
        URI request = URI.create("https://www.example.com/some/path");
        SSLHandshakeException exception = new SSLHandshakeException("PKIX path building failed");

        Optional<URI> fallback = HttpUriReader.certificateHostnameFallback(request, exception);

        assertThat(fallback).isEmpty();
    }

    // ── parentDomainUri ───────────────────────────────────────────────────────

    @Test
    void parentDomainUri_rewritesSubdomainToParentDomain() {
        URI request = URI.create("https://en.example.org/some/path?x=1#section");

        Optional<URI> fallback = HttpUriReader.parentDomainUri(request);

        assertThat(fallback).hasValue(URI.create("https://example.org/some/path?x=1#section"));
    }

    // ── unresolvedHostFallback ────────────────────────────────────────────────

    @Test
    void unresolvedHostFallback_rewritesWwwHostOnUnresolvedAddress() {
        URI request = URI.create("https://www.example.net/some/path?x=1#section");
        ConnectException exception = new ConnectException("connect failed");
        exception.initCause(new java.nio.channels.UnresolvedAddressException());

        Optional<URI> fallback = HttpUriReader.unresolvedHostFallback(request, exception);

        assertThat(fallback).hasValue(URI.create("https://example.net/some/path?x=1#section"));
    }

    @Test
    void unresolvedHostFallback_returnsEmptyForNonWwwHost() {
        URI request = URI.create("https://example.net/some/path");
        ConnectException exception = new ConnectException("connect failed");
        exception.initCause(new java.nio.channels.UnresolvedAddressException());

        Optional<URI> fallback = HttpUriReader.unresolvedHostFallback(request, exception);

        assertThat(fallback).isEmpty();
    }

    @Test
    void unresolvedHostFallback_returnsEmptyForOtherConnectErrors() {
        URI request = URI.create("https://www.example.net/some/path");
        ConnectException exception = new ConnectException("connection refused");

        Optional<URI> fallback = HttpUriReader.unresolvedHostFallback(request, exception);

        assertThat(fallback).isEmpty();
    }

    // ── certificateDnsFallback ────────────────────────────────────────────────

    @Test
    void certificateDnsFallback_usesSingleRelatedSanHost() {
        URI request = URI.create("https://en.example.org/some/path");

        Optional<URI> fallback = HttpUriReader.certificateDnsFallback(request, List.of("example.org", "gallery.example.org"));

        assertThat(fallback).hasValue(URI.create("https://example.org/some/path"));
    }

    @Test
    void certificateDnsFallback_returnsEmptyWhenSansAreAmbiguous() {
        URI request = URI.create("https://en.example.org/some/path");

        Optional<URI> fallback = HttpUriReader.certificateDnsFallback(
                request,
                List.of("www.example.org", "www.cs.example.org")
        );

        assertThat(fallback).isEmpty();
    }

    @Test
    void certificateDnsFallback_ignoresUnrelatedSiblingSubdomains() {
        URI request = URI.create("https://en.example.org/some/path");

        Optional<URI> fallback = HttpUriReader.certificateDnsFallback(request, List.of("gallery.example.org"));

        assertThat(fallback).isEmpty();
    }

    @Test
    void certificateDnsFallback_allowsCloseFuzzySubdomainMatchesForLongLabels() {
        URI request = URI.create("https://english.example.org/some/path");

        Optional<URI> fallback = HttpUriReader.certificateDnsFallback(request, List.of("engllish.example.org"));

        assertThat(fallback).hasValue(URI.create("https://engllish.example.org/some/path"));
    }

    @Test
    void certificateDnsFallback_disallowsFuzzyMatchesForShortLabels() {
        URI request = URI.create("https://en.example.org/some/path");

        Optional<URI> fallback = HttpUriReader.certificateDnsFallback(request, List.of("eg.example.org"));

        assertThat(fallback).isEmpty();
    }

    // ── extractDnsNames ───────────────────────────────────────────────────────

    @Test
    void extractDnsNames_readsDnsSubjectAlternativeNames() {
        X509Certificate certificate = new StubX509Certificate(List.of(
                List.of(2, "www.example.org"),
                List.of(7, "127.0.0.1"),
                List.of(2, "example.org")
        ));

        List<String> dnsNames = HttpUriReader.extractDnsNames(new java.security.cert.Certificate[]{certificate});

        assertThat(dnsNames).containsExactly("www.example.org", "example.org");
    }

    @Test
    void extractDnsNames_nullCertificates_returnsEmptyList() {
        assertThat(HttpUriReader.extractDnsNames(null)).isEmpty();
    }

    @Test
    void extractDnsNames_emptyCertificateArray_returnsEmptyList() {
        assertThat(HttpUriReader.extractDnsNames(new java.security.cert.Certificate[0])).isEmpty();
    }

    @Test
    void extractDnsNames_nonX509FirstCertificate_returnsEmptyList() {
        java.security.cert.Certificate nonX509 = new java.security.cert.Certificate("STUB") {
            public byte[] getEncoded() { return new byte[0]; }
            public void verify(PublicKey key) {}
            public void verify(PublicKey key, String provider) {}
            public String toString() { return "stub"; }
            public PublicKey getPublicKey() { return null; }
        };

        assertThat(HttpUriReader.extractDnsNames(new java.security.cert.Certificate[]{nonX509})).isEmpty();
    }

    @Test
    void extractDnsNames_certificateWithNullSans_returnsEmptyList() {
        X509Certificate certificate = new StubX509Certificate(null);

        assertThat(HttpUriReader.extractDnsNames(new java.security.cert.Certificate[]{certificate})).isEmpty();
    }

    // ── isNoIndex ─────────────────────────────────────────────────────────────

    @Test
    void isNoIndex_noindexDirective_returnsTrue() {
        assertThat(HttpUriReader.isNoIndex("noindex")).isTrue();
    }

    @Test
    void isNoIndex_noneDirective_returnsTrue() {
        assertThat(HttpUriReader.isNoIndex("none")).isTrue();
    }

    @Test
    void isNoIndex_uppercaseDirective_returnsTrue() {
        assertThat(HttpUriReader.isNoIndex("NOINDEX")).isTrue();
    }

    @Test
    void isNoIndex_noindexAmongOtherDirectives_returnsTrue() {
        assertThat(HttpUriReader.isNoIndex("follow, noindex")).isTrue();
    }

    @Test
    void isNoIndex_indexDirective_returnsFalse() {
        assertThat(HttpUriReader.isNoIndex("index, follow")).isFalse();
    }

    // ── read() ────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void read_status429_throwsRateLimitException() throws Exception {
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(429);
        when(response.headers()).thenReturn(
                HttpHeaders.of(Map.of("Retry-After", List.of("60")), (k, v) -> true));

        assertThatThrownBy(() -> reader.read(URI.create("https://example.com/")))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("429");
    }

    @Test
    @SuppressWarnings("unchecked")
    void read_status503_throwsRateLimitException() throws Exception {
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(503);
        when(response.headers()).thenReturn(
                HttpHeaders.of(Map.of("Retry-After", List.of("30")), (k, v) -> true));

        assertThatThrownBy(() -> reader.read(URI.create("https://example.com/")))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("503");
    }

    @Test
    @SuppressWarnings("unchecked")
    void read_status202_throwsDeferredContentException() throws Exception {
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(202);

        assertThatThrownBy(() -> reader.read(URI.create("https://example.com/")))
                .isInstanceOf(DeferredContentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void read_status403_throwsIOException() throws Exception {
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(403);

        assertThatThrownBy(() -> reader.read(URI.create("https://example.com/")))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("403");
    }

    @Test
    @SuppressWarnings("unchecked")
    void read_status404_throwsIOException() throws Exception {
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(404);

        assertThatThrownBy(() -> reader.read(URI.create("https://example.com/")))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("404");
    }

    @Test
    @SuppressWarnings("unchecked")
    void read_status200_nonHtmlContent_returnsData() throws Exception {
        byte[] bytes = {1, 2, 3};
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(
                HttpHeaders.of(Map.of("Content-Type", List.of("application/pdf")), (k, v) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(bytes));

        UriReader.Data data = reader.read(URI.create("https://example.com/doc.pdf"));

        assertThat(data).isNotNull();
        assertThat(data.data()).isEqualTo(bytes);
    }

    @Test
    @SuppressWarnings("unchecked")
    void read_status200_htmlContent_returnsData() throws Exception {
        byte[] html = "<html><body>hello world</body></html>".getBytes();
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(
                HttpHeaders.of(Map.of("Content-Type", List.of("text/html; charset=UTF-8")), (k, v) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(html));

        UriReader.Data data = reader.read(URI.create("https://example.com/page"));

        assertThat(data).isNotNull();
        assertThat(data.data()).isEqualTo(html);
    }

    @Test
    @SuppressWarnings("unchecked")
    void read_status200_htmlWithXRobotsNoindex_throwsRobotsDisallowedException() throws Exception {
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(
                Map.of("Content-Type", List.of("text/html"),
                       "X-Robots-Tag", List.of("noindex")),
                (k, v) -> true));

        assertThatThrownBy(() -> reader.read(URI.create("https://example.com/page")))
                .isInstanceOf(RobotsDisallowedException.class);
    }

    // ── StubX509Certificate ───────────────────────────────────────────────────

    private static final class StubX509Certificate extends X509Certificate {

        private final Collection<List<?>> subjectAlternativeNames;

        private StubX509Certificate(Collection<List<?>> subjectAlternativeNames) {
            this.subjectAlternativeNames = subjectAlternativeNames;
        }

        @Override
        public Collection<List<?>> getSubjectAlternativeNames() throws CertificateParsingException {
            return subjectAlternativeNames;
        }

        @Override
        public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {
        }

        @Override
        public void checkValidity(Date date) throws CertificateExpiredException, CertificateNotYetValidException {
        }

        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public java.math.BigInteger getSerialNumber() {
            return java.math.BigInteger.ONE;
        }

        @Override
        public Principal getIssuerDN() {
            return null;
        }

        @Override
        public Principal getSubjectDN() {
            return null;
        }

        @Override
        public Date getNotBefore() {
            return new Date();
        }

        @Override
        public Date getNotAfter() {
            return new Date();
        }

        @Override
        public byte[] getTBSCertificate() throws CertificateEncodingException {
            return new byte[0];
        }

        @Override
        public byte[] getSignature() {
            return new byte[0];
        }

        @Override
        public String getSigAlgName() {
            return "";
        }

        @Override
        public String getSigAlgOID() {
            return "";
        }

        @Override
        public byte[] getSigAlgParams() {
            return new byte[0];
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return new boolean[0];
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return new boolean[0];
        }

        @Override
        public boolean[] getKeyUsage() {
            return new boolean[0];
        }

        @Override
        public int getBasicConstraints() {
            return 0;
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            return new byte[0];
        }

        @Override
        public void verify(PublicKey key) {
        }

        @Override
        public void verify(PublicKey key, String sigProvider) {
        }

        @Override
        public String toString() {
            return "StubX509Certificate";
        }

        @Override
        public PublicKey getPublicKey() {
            return null;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        @Override
        public java.util.Set<String> getCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public java.util.Set<String> getNonCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            return new byte[0];
        }
    }
}
