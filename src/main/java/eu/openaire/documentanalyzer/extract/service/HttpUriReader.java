package eu.openaire.documentanalyzer.extract.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

@Component
public class HttpUriReader implements UriReader {

    private static final Logger logger = LoggerFactory.getLogger(HttpUriReader.class);
    private final HttpClient client;

    public HttpUriReader() throws KeyManagementException, NoSuchAlgorithmException {
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
                .followRedirects(HttpClient.Redirect.NORMAL)
                .sslContext(sslContext)
                .build();
    }

    @Override
    public byte[] read(URI uri) throws IOException {
        try {
            logger.info("Reading url: {}", uri);
            HttpRequest request = HttpRequest
                    .newBuilder(uri)
                    .header("User-Agent", "Mozilla/5.0 (Java HttpClient)")
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new RuntimeException(response.toString());
            }
            byte[] bytes = response.body().readAllBytes();

            Document doc = Jsoup.parse(new String(bytes, detectCharset(response)));

            String englishUrl = detectEnglishHtmlVersion(doc);
            if (englishUrl != null && !englishUrl.equals(uri.toString())) {
                return read(URI.create(englishUrl));
            }
            return bytes;

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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
