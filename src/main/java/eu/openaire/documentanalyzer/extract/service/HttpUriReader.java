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
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Optional;

public class HttpUriReader implements UriReader {

    private static final Logger logger = LoggerFactory.getLogger(HttpUriReader.class);
    private final HttpClient client;
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
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());

        this.client = HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .sslContext(sslContext)
                .build();
    }

    @Override
    public Data read(URI uri) throws IOException {
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
                return read(URI.create(englishUrl));
            }
            return new Data(uri, bytes);

        } catch (SSLHandshakeException e) {
            Optional<URI> fallbackUri = certificateHostnameFallback(uri, e);
            if (fallbackUri.isPresent()) {
                logger.warn("Retrying {} as {} due to TLS hostname mismatch", uri, fallbackUri.get());
                return read(fallbackUri.get());
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

    // TODO: check this method
    //   retry bare host on TLS SAN mismatch for www URLs
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

    private static boolean isHostnameMismatch(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("No subject alternative DNS name matching")) {
                return true;
            }
        }
        return false;
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
