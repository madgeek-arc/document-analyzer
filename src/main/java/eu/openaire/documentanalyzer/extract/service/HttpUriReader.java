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
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
                    .header("User-Agent", "Mozilla/5.0 (Java HttpClient)")
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            int status = response.statusCode();
            if (status == 429 || status == 503) {
                String retryAfter = response.headers().firstValue("Retry-After").orElse("unknown");
                throw new RateLimitException("Rate limited (" + status + ") by " + uri + " — Retry-After: " + retryAfter);
            } else if (status == 403) {
                throw new IOException("Access denied (403): " + uri);
            } else if (status != 200) {
                throw new IOException("Unexpected HTTP status " + status + " for " + uri);
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

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading " + uri, e);
        }
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
