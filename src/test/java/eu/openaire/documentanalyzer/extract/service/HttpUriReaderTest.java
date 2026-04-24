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

import java.net.ConnectException;
import javax.net.ssl.SSLHandshakeException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.net.URI;
import java.security.Principal;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Date;
import java.util.List;
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

    @Test
    void parentDomainUri_rewritesSubdomainToParentDomain() {
        URI request = URI.create("https://en.uoc.gr/some/path?x=1#section");

        Optional<URI> fallback = HttpUriReader.parentDomainUri(request);

        assertThat(fallback).hasValue(URI.create("https://uoc.gr/some/path?x=1#section"));
    }

    @Test
    void unresolvedHostFallback_rewritesWwwHostOnUnresolvedAddress() {
        URI request = URI.create("https://www.nlg.gr/some/path?x=1#section");
        ConnectException exception = new ConnectException("connect failed");
        exception.initCause(new java.nio.channels.UnresolvedAddressException());

        Optional<URI> fallback = HttpUriReader.unresolvedHostFallback(request, exception);

        assertThat(fallback).hasValue(URI.create("https://nlg.gr/some/path?x=1#section"));
    }

    @Test
    void unresolvedHostFallback_returnsEmptyForNonWwwHost() {
        URI request = URI.create("https://nlg.gr/some/path");
        ConnectException exception = new ConnectException("connect failed");
        exception.initCause(new java.nio.channels.UnresolvedAddressException());

        Optional<URI> fallback = HttpUriReader.unresolvedHostFallback(request, exception);

        assertThat(fallback).isEmpty();
    }

    @Test
    void unresolvedHostFallback_returnsEmptyForOtherConnectErrors() {
        URI request = URI.create("https://www.nlg.gr/some/path");
        ConnectException exception = new ConnectException("connection refused");

        Optional<URI> fallback = HttpUriReader.unresolvedHostFallback(request, exception);

        assertThat(fallback).isEmpty();
    }

    @Test
    void certificateDnsFallback_usesSingleRelatedSanHost() {
        URI request = URI.create("https://en.uoc.gr/some/path");

        Optional<URI> fallback = HttpUriReader.certificateDnsFallback(request, List.of("uoc.gr", "gallery.uoc.gr"));

        assertThat(fallback).hasValue(URI.create("https://uoc.gr/some/path"));
    }

    @Test
    void certificateDnsFallback_returnsEmptyWhenSansAreAmbiguous() {
        URI request = URI.create("https://en.uoc.gr/some/path");

        Optional<URI> fallback = HttpUriReader.certificateDnsFallback(
                request,
                List.of("www.uoc.gr", "www.cs.uoc.gr")
        );

        assertThat(fallback).isEmpty();
    }

    @Test
    void certificateDnsFallback_ignoresUnrelatedSiblingSubdomains() {
        URI request = URI.create("https://en.uoc.gr/some/path");

        Optional<URI> fallback = HttpUriReader.certificateDnsFallback(request, List.of("gallery.uoc.gr"));

        assertThat(fallback).isEmpty();
    }

    @Test
    void certificateDnsFallback_allowsCloseFuzzySubdomainMatchesForLongLabels() {
        URI request = URI.create("https://english.uoc.gr/some/path");

        Optional<URI> fallback = HttpUriReader.certificateDnsFallback(request, List.of("engllish.uoc.gr"));

        assertThat(fallback).hasValue(URI.create("https://engllish.uoc.gr/some/path"));
    }

    @Test
    void certificateDnsFallback_disallowsFuzzyMatchesForShortLabels() {
        URI request = URI.create("https://en.uoc.gr/some/path");

        Optional<URI> fallback = HttpUriReader.certificateDnsFallback(request, List.of("eg.uoc.gr"));

        assertThat(fallback).isEmpty();
    }

    @Test
    void extractDnsNames_readsDnsSubjectAlternativeNames() {
        X509Certificate certificate = new StubX509Certificate(List.of(
                List.of(2, "www.uoc.gr"),
                List.of(7, "127.0.0.1"),
                List.of(2, "uoc.gr")
        ));

        List<String> dnsNames = HttpUriReader.extractDnsNames(new java.security.cert.Certificate[]{certificate});

        assertThat(dnsNames).containsExactly("www.uoc.gr", "uoc.gr");
    }

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
