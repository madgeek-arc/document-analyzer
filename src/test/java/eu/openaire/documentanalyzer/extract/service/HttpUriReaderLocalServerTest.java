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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link HttpUriReader} against an in-JVM HTTP server on the loopback interface.
 */
class HttpUriReaderLocalServerTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/final", exchange -> {
            byte[] body = "<html><body>final</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirect-to-localhost", exchange -> {
            exchange.getResponseHeaders().add("Location",
                    "http://localhost:" + server.getAddress().getPort() + "/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/loop", exchange -> {
            exchange.getResponseHeaders().add("Location", "/loop");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static HttpReaderSettings settings(Duration requestTimeout) {
        return new HttpReaderSettings(0, Duration.ofSeconds(2), requestTimeout, false, true);
    }

    @Test
    void read_followsRedirects() throws Exception {
        HttpUriReader reader = new HttpUriReader(settings(Duration.ofSeconds(5)));

        UriReader.Data data = reader.read(URI.create(base + "/redirect"));

        assertThat(data.uri().getPath()).isEqualTo("/final");
        assertThat(new String(data.data(), StandardCharsets.UTF_8)).contains("final");
    }

    @Test
    void read_checksEveryRedirectHop() throws Exception {
        UrlGuard onlyLoopbackLiteral = new UrlGuard(false) {
            @Override
            public void check(URI uri) throws BlockedUrlException {
                if (!"127.0.0.1".equals(uri.getHost())) {
                    super.check(uri);
                }
            }
        };
        HttpUriReader reader = new HttpUriReader(settings(Duration.ofSeconds(5)), onlyLoopbackLiteral);

        assertThatThrownBy(() -> reader.read(URI.create(base + "/redirect-to-localhost")))
                .isInstanceOf(BlockedUrlException.class);
    }

    @Test
    void read_rejectsPrivateAddressByDefault() throws Exception {
        HttpUriReader reader = new HttpUriReader(HttpReaderSettings.defaults());

        assertThatThrownBy(() -> reader.read(URI.create(base + "/final")))
                .isInstanceOf(BlockedUrlException.class);
    }

    @Test
    void read_stopsRedirectLoops() throws Exception {
        HttpUriReader reader = new HttpUriReader(settings(Duration.ofSeconds(5)));

        assertThatThrownBy(() -> reader.read(URI.create(base + "/loop")))
                .hasMessageContaining("Too many redirects");
    }

    @Test
    void read_appliesRequestTimeout() throws Exception {
        HttpUriReader reader = new HttpUriReader(settings(Duration.ofMillis(300)));

        assertThatThrownBy(() -> reader.read(URI.create(base + "/slow")))
                .isInstanceOf(HttpTimeoutException.class);
    }
}
