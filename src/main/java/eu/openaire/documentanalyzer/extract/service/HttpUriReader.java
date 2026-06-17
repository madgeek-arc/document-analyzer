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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.ConnectException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class HttpUriReader implements UriReader {

    private static final Logger logger = LoggerFactory.getLogger(HttpUriReader.class);
    private final HttpClient client;
    private final SSLContext sslContext;
    private final long requestDelayMs;

    public HttpUriReader() throws KeyManagementException, NoSuchAlgorithmException {
        this(0);
    }

    public HttpUriReader(long requestDelayMs) throws KeyManagementException, NoSuchAlgorithmException {
        this.requestDelayMs = requestDelayMs;
        // Trust all certificates
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };
        // Create an SSLContext that uses the trust manager
        this.sslContext = SSLContext.getInstance("TLS");
        this.sslContext.init(null, trustAllCerts, new SecureRandom());

        this.client = HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .sslContext(sslContext)
                .build();
    }

    @Override
    public Data read(URI uri) throws IOException {
        return read(uri, new LinkedHashSet<>());
    }

    private Data read(URI uri, Set<String> attemptedHosts) throws IOException {
        String host = uri.getHost();
        if (host != null) {
            attemptedHosts.add(host.toLowerCase());
        }
        if (requestDelayMs > 0) {
            try {
                Thread.sleep(requestDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting before request to " + uri, e);
            }
        }
        try {
            logger.info("Reading url: {}", uri);
            HttpRequest request = HttpRequest
                    .newBuilder(uri)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            int status = response.statusCode();
            if (status == 429 || status == 503) {
                String retryAfter = response.headers().firstValue("Retry-After").orElse("unknown");
                throw new RateLimitException("Rate limited (" + status + ") by " + uri + " — Retry-After: " + retryAfter);
            } else if (status == 202) {
                throw new DeferredContentException("Deferred response (202) for " + uri + " — use Playwright to render");
            } else if (status == 403) {
                throw new IOException("Access denied (403): " + uri);
            } else if (status != 200) {
                throw new IOException("Unexpected HTTP status " + status + " for " + uri);
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (contentType.contains("text/html")) {
                Optional<String> xRobotsTag = response.headers().firstValue("X-Robots-Tag");
                if (xRobotsTag.isPresent() && isNoIndex(xRobotsTag.get())) {
                    throw new RobotsDisallowedException("X-Robots-Tag disallows indexing: " + uri);
                }
            }

            byte[] bytes;
            try (InputStream is = response.body()) {
                bytes = is.readAllBytes();
            }

            Document doc = Jsoup.parse(new String(bytes, detectCharset(response)));

            String englishUrl = detectEnglishHtmlVersion(doc);
            if (englishUrl != null && !englishUrl.equals(uri.toString())) {
                return read(URI.create(englishUrl), attemptedHosts);
            }
            return new Data(uri, bytes);

        } catch (SSLHandshakeException e) {
            Optional<URI> fallbackUri = certificateHostnameFallback(uri, e)
                    .filter(candidate -> hasNotBeenAttempted(candidate, attemptedHosts));
            if (fallbackUri.isPresent()) {
                logger.warn("Retrying {} as {} due to TLS hostname mismatch", uri, fallbackUri.get());
                return read(fallbackUri.get(), attemptedHosts);
            }

            Optional<URI> parentDomainUri = parentDomainUri(uri)
                    .filter(candidate -> hasNotBeenAttempted(candidate, attemptedHosts));
            if (parentDomainUri.isPresent()) {
                logger.warn("Retrying {} as {} due to TLS hostname mismatch on subdomain", uri, parentDomainUri.get());
                return read(parentDomainUri.get(), attemptedHosts);
            }

            List<String> dnsNames = certificateDnsNames(uri);
            if (!dnsNames.isEmpty()) {
                logger.warn("Certificate for {} is valid for DNS names {}", uri, dnsNames);
                Optional<URI> certificateHostFallback = certificateDnsFallback(uri, dnsNames)
                        .filter(candidate -> hasNotBeenAttempted(candidate, attemptedHosts));
                if (certificateHostFallback.isPresent()) {
                    logger.warn("Retrying {} as {} based on certificate SANs", uri, certificateHostFallback.get());
                    return read(certificateHostFallback.get(), attemptedHosts);
                }
            }
            throw e;
        } catch (ConnectException e) {
            Optional<URI> fallbackUri = unresolvedHostFallback(uri, e)
                    .filter(candidate -> hasNotBeenAttempted(candidate, attemptedHosts));
            if (fallbackUri.isPresent()) {
                logger.warn("Retrying {} as {} due to unresolved host", uri, fallbackUri.get());
                return read(fallbackUri.get(), attemptedHosts);
            }
            throw e;
        } catch (RobotsDisallowedException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading " + uri, e);
        }
    }

    /**
     * Returns {@code true} if the given robots directive string contains {@code noindex} or
     * {@code none} (which implies noindex). Handles comma/semicolon-separated directives and
     * optional whitespace.
     *
     * @param directives the value of an {@code X-Robots-Tag} header or {@code <meta name="robots">} content
     * @return whether the directive instructs bots not to index the page
     */
    static boolean isNoIndex(String directives) {
        for (String token : directives.toLowerCase().split("[,;\\s]+")) {
            if (token.equals("noindex") || token.equals("none")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a retry URI for TLS hostname mismatches when the request host starts with {@code www.}.
     *
     * <p>This is the narrowest fallback for misconfigured certificates such as
     * {@code www.example.org -> example.org}: if Java reports a SAN hostname mismatch and the
     * original host is a {@code www} variant, the method rewrites the request to the bare host.
     * It does not attempt broader host discovery.
     *
     * @param uri the original request URI
     * @param exception the TLS handshake failure
     * @return the bare-host retry URI when the mismatch matches this pattern; otherwise empty
     */
    static Optional<URI> certificateHostnameFallback(URI uri, SSLHandshakeException exception) {
        String host = uri.getHost();
        if (host == null || !host.startsWith("www.") || host.length() <= 4) {
            return Optional.empty();
        }
        if (!isHostnameMismatch(exception)) {
            return Optional.empty();
        }

        String fallbackHost = host.substring(4);
        try {
            return Optional.of(new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    fallbackHost,
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            ));
        } catch (URISyntaxException e) {
            logger.debug("Could not build fallback URI for {}", uri, e);
            return Optional.empty();
        }
    }

    /**
     * Returns a retry URI for unresolved-address failures when the request host starts with
     * {@code www.}.
     *
     * <p>This mirrors {@link #certificateHostnameFallback(URI, SSLHandshakeException)} for DNS or
     * address-resolution failures: if {@code www.example.org} cannot be resolved, retry once as
     * {@code example.org}. If the host is not a {@code www} variant, or the connect failure was
     * caused by something other than {@code UnresolvedAddressException}, no retry URI is produced.
     *
     * @param uri the original request URI
     * @param exception the connection failure
     * @return the bare-host retry URI for unresolved {@code www} hosts; otherwise empty
     */
    static Optional<URI> unresolvedHostFallback(URI uri, ConnectException exception) {
        String host = uri.getHost();
        if (host == null || !host.startsWith("www.") || host.length() <= 4) {
            return Optional.empty();
        }
        if (!isUnresolvedAddress(exception)) {
            return Optional.empty();
        }
        return rewriteWithoutLeadingWww(uri);
    }

    /**
     * Chooses a retry host from certificate DNS SAN entries after a TLS hostname mismatch.
     *
     * <p>The matching strategy is intentionally conservative. It prefers:
     * {@code www} / non-{@code www} variants first, then a parent-domain fallback such as
     * {@code en.example.org -> example.org}, and finally a bounded fuzzy match on subdomain labels.
     * Fuzzy matching is only allowed for longer labels so short labels such as {@code en} are not
     * rewritten to unrelated sibling subdomains.
     *
     * <p>If multiple SAN entries are plausible, the method returns empty rather than guessing.
     *
     * @param uri the original request URI
     * @param dnsNames DNS SAN entries extracted from the certificate
     * @return a single safe retry URI when one can be inferred; otherwise empty
     */
    static Optional<URI> certificateDnsFallback(URI uri, Collection<String> dnsNames) {
        String host = uri.getHost();
        if (host == null || dnsNames.isEmpty()) {
            return Optional.empty();
        }

        String normalizedHost = host.toLowerCase();
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String dnsName : dnsNames) {
            if (dnsName == null || dnsName.isBlank()) {
                continue;
            }
            String normalized = dnsName.toLowerCase();
            if (normalized.contains("*") || normalized.equals(normalizedHost)) {
                continue;
            }
            candidates.add(normalized);
        }

        String bareHost = normalizedHost.startsWith("www.") ? normalizedHost.substring(4) : normalizedHost;
        if (normalizedHost.startsWith("www.") && candidates.contains(bareHost)) {
            return rewriteHost(uri, bareHost);
        }
        String wwwHost = "www." + normalizedHost;
        if (!normalizedHost.startsWith("www.") && candidates.contains(wwwHost)) {
            return rewriteHost(uri, wwwHost);
        }
        Optional<String> parentDomainFallback = parentDomainFallback(normalizedHost, candidates);
        if (parentDomainFallback.isPresent()) {
            return rewriteHost(uri, parentDomainFallback.get());
        }
        return fuzzySubdomainFallback(normalizedHost, candidates)
                .flatMap(candidate -> rewriteHost(uri, candidate));
    }

    /**
     * Returns a retry URI for subdomains by removing exactly one leftmost label.
     *
     * <p>This is used as a conservative fallback for requests like {@code en.example.org} when the
     * subdomain hostname itself fails TLS verification but the parent domain may still be the
     * intended site. The method only applies to hosts with more than two labels and removes a
     * single leading label per retry step.
     *
     * @param uri the original request URI
     * @return the parent-domain retry URI, or empty for bare domains and invalid hosts
     */
    static Optional<URI> parentDomainUri(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return Optional.empty();
        }
        String parentDomain = parentDomain(host.toLowerCase());
        if (parentDomain == null) {
            return Optional.empty();
        }
        return rewriteHost(uri, parentDomain);
    }

    private static boolean isHostnameMismatch(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("No subject alternative DNS name matching")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnresolvedAddress(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof java.nio.channels.UnresolvedAddressException) {
                return true;
            }
        }
        return false;
    }

    private static Optional<URI> rewriteWithoutLeadingWww(URI uri) {
        String host = uri.getHost();
        if (host == null || !host.startsWith("www.") || host.length() <= 4) {
            return Optional.empty();
        }
        return rewriteHost(uri, host.substring(4));
    }

    private static Optional<URI> rewriteHost(URI uri, String newHost) {
        if (newHost == null || newHost.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    newHost,
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            ));
        } catch (URISyntaxException e) {
            logger.debug("Could not build fallback URI for {}", uri, e);
            return Optional.empty();
        }
    }

    /**
     * Inspects the server certificate for an HTTPS URI and extracts DNS SAN entries.
     *
     * <p>This opens a direct TLS socket using the same permissive SSL context as the HTTP client,
     * allowing the code to inspect the peer certificate even when hostname verification would fail
     * at the higher HTTP layer. The result is used only for diagnostics and conservative retry
     * selection after a hostname mismatch.
     *
     * @param uri the HTTPS URI whose certificate should be inspected
     * @return DNS SAN entries from the peer certificate, or an empty list when they cannot be read
     */
    private List<String> certificateDnsNames(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return List.of();
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 443;
        try (SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), port), 10000);
            socket.setSoTimeout(10000);
            socket.startHandshake();
            return extractDnsNames(socket.getSession().getPeerCertificates());
        } catch (IOException e) {
            logger.debug("Could not inspect certificate SANs for {}", uri, e);
            return List.of();
        }
    }

    /**
     * Extracts DNS-name subject alternative names from the first X.509 certificate in the chain.
     *
     * <p>Only SAN entries of type {@code dNSName} are returned. IP-address SANs and other entry
     * types are ignored because hostname retry logic only operates on DNS hosts.
     *
     * @param certificates the peer certificate chain
     * @return DNS SAN values in certificate order, or an empty list when unavailable
     */
    static List<String> extractDnsNames(java.security.cert.Certificate[] certificates) {
        if (certificates == null || certificates.length == 0 || !(certificates[0] instanceof X509Certificate certificate)) {
            return List.of();
        }
        try {
            Collection<List<?>> subjectAlternativeNames = certificate.getSubjectAlternativeNames();
            if (subjectAlternativeNames == null) {
                return List.of();
            }
            List<String> dnsNames = new ArrayList<>();
            for (List<?> entry : subjectAlternativeNames) {
                if (entry.size() < 2) {
                    continue;
                }
                Object type = entry.getFirst();
                Object value = entry.get(1);
                if (type instanceof Integer kind && kind == 2 && value instanceof String dnsName && !dnsName.isBlank()) {
                    dnsNames.add(dnsName);
                }
            }
            return dnsNames;
        } catch (CertificateParsingException e) {
            logger.debug("Could not parse certificate SANs", e);
            return List.of();
        }
    }

    private static boolean hasNotBeenAttempted(URI uri, Set<String> attemptedHosts) {
        return uri.getHost() != null && !attemptedHosts.contains(uri.getHost().toLowerCase());
    }

    private static Optional<String> parentDomainFallback(String host, Set<String> candidates) {
        String parentDomain = parentDomain(host);
        if (parentDomain == null) {
            return Optional.empty();
        }
        return candidates.contains(parentDomain) ? Optional.of(parentDomain) : Optional.empty();
    }

    private static Optional<String> fuzzySubdomainFallback(String host, Set<String> candidates) {
        String baseDomain = registrableDomain(host);
        if (baseDomain == null) {
            return Optional.empty();
        }
        List<RankedCandidate> rankedCandidates = candidates.stream()
                .filter(candidate -> !candidate.equals(host))
                .filter(candidate -> baseDomain.equals(registrableDomain(candidate)))
                .map(candidate -> rankFuzzyCandidate(host, candidate))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparingInt(RankedCandidate::score)
                        .thenComparing(RankedCandidate::host))
                .toList();
        if (rankedCandidates.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(rankedCandidates.getFirst().host());
    }

    private static Optional<RankedCandidate> rankFuzzyCandidate(String host, String candidate) {
        String[] hostLabels = host.split("\\.");
        String[] candidateLabels = candidate.split("\\.");
        if (hostLabels.length != candidateLabels.length) {
            return Optional.empty();
        }
        if (hostLabels.length < 3) {
            return Optional.empty();
        }

        int subdomainLabels = hostLabels.length - 2;
        int score = 0;
        for (int i = 0; i < subdomainLabels; i++) {
            String left = hostLabels[i];
            String right = candidateLabels[i];
            if (left.equals(right)) {
                continue;
            }
            if (left.length() < 4 || right.length() < 4) {
                return Optional.empty();
            }
            int distance = levenshteinDistance(left, right);
            int maxDistance = Math.min(2, Math.max(left.length(), right.length()) / 4);
            if (distance <= 0 || distance > maxDistance) {
                return Optional.empty();
            }
            score += distance;
        }
        return score == 0 ? Optional.empty() : Optional.of(new RankedCandidate(candidate, score));
    }

    private static String parentDomain(String host) {
        String[] labels = host.split("\\.");
        if (labels.length <= 2) {
            return null;
        }
        return String.join(".", java.util.Arrays.copyOfRange(labels, 1, labels.length));
    }

    private static String registrableDomain(String host) {
        String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return null;
        }
        return labels[labels.length - 2] + "." + labels[labels.length - 1];
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

    private record RankedCandidate(String host, int score) {
    }

    private String detectCharset(HttpResponse<?> response) {
        Optional<String> contentType = response.headers().firstValue("Content-Type");
        String charset = "UTF-8";
        if (contentType.isPresent()) {
            String ct = contentType.get();
            logger.debug("Content-Type header: {}", ct);

            if (ct.toLowerCase().contains("charset=")) {
                String[] parts = ct.split("charset=");
                if (parts.length > 1) {
                    charset = parts[1].split("[;\\s]")[0].trim();
                }
            }

        } else {
            logger.debug("No Content-Type header found");
        }
        return charset;
    }

    /**
     * Reads webpage from provided uri and (if found) returns the English page equivalent uri.
     * Otherwise, returns the provided url.
     *
     * @param uri the uri of the website to search
     * @return the English equivalent webpage url, or the provided uri
     * @throws IOException
     */
    public String detectEnglishHtmlVersion(URI uri) throws IOException {
        return read(uri).uri().toString();
    }

    private String detectEnglishHtmlVersion(Document doc) {
        Elements hreflangs = doc.select("link[rel=alternate][hreflang]");
        for (Element el : hreflangs) {
            String lang = el.attr("hreflang").toLowerCase();
            if (lang.equals("en") || lang.startsWith("en-")) {
                logger.debug("Found english version available at: {}", el.attr("href"));
                return el.attr("href");
            }
        }
        return null;
    }

}
