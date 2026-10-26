# Document Analyzer — Integrations

| Direction | Counterpart | Protocol | Purpose | Configuration key | Evidence path/symbol |
|---|---|---|---|---|---|
| provided | Host Spring Boot application (in-process) | Java: `DocumentAnalyzerService#read`, `#generate` | Extract and enrich documents | `scraping.request-delay-ms`, `document-analyzer.*`, `system-prompt`, `spring.ai.openai.*` | `autoconfigure/DocumentAnalyzerAutoConfiguration.java#documentAnalyzerService` |
| provided | HTTP clients (standalone mode only) | REST, `POST /api/v1/documents/extract`, `/enrich` | Same, over HTTP | `server.port` (8666), `server.servlet.context-path` (`/api`) | `analyze/controller/DocumentAnalyzerController.java` |
| consumed | External websites given by the caller (page, `/sitemap.xml`, `/robots.txt`, same-domain links) | HTTP(S): `java.net.http.HttpClient` and Playwright Chromium | Fetch and render public pages only (`UrlGuard`) | `scraping.request-delay-ms`, `document-analyzer.http.*`, `document-analyzer.allow-private-addresses` | `extract/service/HttpUriReader.java#read`, `WebPageContentExtractor.java#extractWholeSite`, `RobotsCache.java#fetchRules` |
| consumed | OpenAI API (external) | HTTPS through a Spring AI `ChatClient` | Fill JSON templates, extract or translate information | `spring.ai.openai.api-key`, `spring.ai.openai.chat.options.model`, `.temperature`, `system-prompt` | `enrich/service/LlmDocumentContentProcessor.java#requestJson` |
| consumed | Playwright browser distribution | Download at first `Playwright.create()` (first page render), or a pre-installed browser | Chromium binaries | Playwright environment variables, for example `PLAYWRIGHT_BROWSERS_PATH`, `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD` | `WebPageContentExtractor#browser` |

Which applications embed it is recorded in their own documentation.

## Provided interfaces

- REST: see [api.md](api.md). The OpenAPI document is generated at runtime by springdoc.
- Java: `DocumentAnalyzerService`
  - `read(URI)`, `read(URI, topics, SupplementaryUrlFilterMethod)` return `Content`
  - `generate(URI, JsonNode template[, topics, method])`, `generate(Content, JsonNode)` return `JsonNode`
  - `close()` shuts down the browser

  The auto-configuration also exposes a `DocumentContentProcessor` bean (`generate`,
  `extractInformation`, `translate`). Its `generate` returns `null` when no valid JSON is obtained.
  Both beans back off when the host defines its own.

## Sequence: enrich

```mermaid
sequenceDiagram
    participant C as Caller
    participant S as DocumentAnalyzerService
    participant H as HttpUriReader
    participant W as WebPageContentExtractor (Chromium)
    participant X as Target website
    participant O as OpenAI
    C->>S: generate(uri, template, topics)
    S->>H: read(uri)
    H->>H: UrlGuard check
    H->>X: GET uri (delay, browser UA, timeouts; redirects re-checked)
    X-->>H: 200 HTML / PDF (or 202 / 4xx / 5xx)
    alt PDF
        S->>S: PdfContentExtractor.extract
    else HTML
        S->>W: extractWholeSite(uri, topics)
        W->>X: render page, GET /sitemap.xml, robots.txt
        loop up to 50 supplementary pages (serialised)
            W->>X: render page
        end
    end
    S->>O: prompt(template + content)
    O-->>S: JSON text (up to 5 requests until valid)
    S-->>C: filled template, or EnrichmentException (502)
```

## Failure behaviour

| Situation | Behaviour | Evidence |
|---|---|---|
| URL not `http`/`https`, or resolves (directly or via a redirect) to a non-public address | `BlockedUrlException`, wrapped in `RuntimeException` by `read`. HTTP 400 in standalone mode | `UrlGuard`, `HttpUriReader#read` |
| Target returns 429/503 | `RateLimitException` (an `IOException`), wrapped in `RuntimeException` by `read`. HTTP 500 in standalone mode | `HttpUriReader#read`, `DocumentAnalyzerService#read` |
| Target returns 202 | Falls back to rendering only that URL with Playwright | `DocumentAnalyzerService#read` |
| Target returns 403 or another non-200 | `IOException` → `RuntimeException` | `HttpUriReader#read` |
| TLS hostname mismatch / unresolved `www.` host | Retries on the bare host, the parent domain, or a host from the certificate SANs, each host at most once | `HttpUriReader#read` |
| Sitemap missing or unparsable | Logged. Continues with the page and its same-domain links | `WebPageContentExtractor#extractWholeSite` |
| Supplementary page fails | Logged and skipped | `WebPageContentExtractor#enrichContentUsingSupplementaryUrls` |
| Playwright navigation timeout | 60 s per navigation, plus up to 5 s per content selector and 5 s network-idle wait | `WebPageContentExtractor#extractFromUrl` |
| Playwright rendering fails | Up to 4 attempts, 2 s backoff doubling. No retry when robots rules or `UrlGuard` forbid the page | `WebPageContentExtractor#extractFromUrl` |
| Slow or unresponsive target (`HttpUriReader`) | Connect timeout 10 s, request timeout 60 s (`document-analyzer.http.*`), then `IOException` | `HttpUriReader`, `HttpUriReaderLocalServerTest` |
| Untrusted or incomplete certificate chain | TLS handshake fails, unless `document-analyzer.http.trust-all-certificates=true` | `HttpUriReader` |
| LLM returns invalid JSON 5 times, or OpenAI fails (auth, quota, network) | The processor logs the error and returns `null`; `DocumentAnalyzerService#generate` throws `EnrichmentException` (HTTP 502) | `LlmDocumentContentProcessor#generate`, `DocumentAnalyzerService#generate` |
| Concurrency | All rendering is serialised on one browser lock, so a whole-site crawl blocks other callers | `WebPageContentExtractor#browserLock` |
