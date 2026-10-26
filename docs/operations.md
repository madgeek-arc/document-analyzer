# Document Analyzer — Operations

Most of this applies both to the standalone service and to hosts that embed the library, since the
same Chromium process and outbound calls run inside the host.

## Start / stop

- `WebPageContentExtractor` launches one headless Chromium on the first page render. If Playwright
  can't create or launch it, that render fails (and is retried). Startup doesn't depend on it.
 (`WebPageContentExtractor#browser`)
- `DocumentAnalyzerService` implements `Closeable`, and Spring calls `close()` on context shutdown,
  which closes the browser and Playwright (Spring infers `close()` as the destroy method of
  `AutoCloseable` beans).

## Health

No actuator is included. In standalone mode, use `GET /api/v3/api-docs` as a liveness probe.
(no `spring-boot-starter-actuator` in `pom.xml`)

## Logging

| Logger | Level | Content |
|---|---|---|
| `eu.openaire.documentanalyzer.extract.service.HttpUriReader` | INFO | `Reading url: <url>` for every fetch. WARN for host fallbacks |
| `...WebPageContentExtractor` | INFO/WARN/ERROR | `Sitemap not found`, `Limited supplementary URLs ...`, `Rendering ... failed (attempt n/4)`, `Blocked browser request to ...`, skipped pages with stack traces |
| `...analyze.service.DocumentAnalyzerService` | DEBUG | The LLM-generated JSON for every `generate` call |
| `...enrich.service.LlmDocumentContentProcessor` | WARN/ERROR | Invalid JSON responses (WARN per attempt), `Could not generate document metadata` (ERROR) |
| `org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor` | DEBUG | Full prompts (document content) and responses, when DEBUG is enabled |

## Metrics and alerting

None built in. Watch for repeated `ERROR` from `LlmDocumentContentProcessor` (OpenAI failures), for
`RateLimitException`/`Access denied (403)` messages, and for TLS handshake failures (sites that may need
`document-analyzer.http.trust-all-certificates`).

## Capacity

- Rendering is serialised on one browser, so throughput is one page at a time per process. A
  whole-site request renders up to 51 pages with `scraping.request-delay-ms` between them, which can
  take minutes (the default delay is 500 ms, plus the render time of each page).
- Chromium memory use grows with page complexity. Size containers accordingly.
- OpenAI cost scales with the amount of extracted text sent per request (page plus supplementary
  pages).

## Background jobs, data, backups

There are no schedulers, queues or persistent data. The `RobotsCache` lives in memory and is reset on
restart.
