# Document Analyzer — Configuration

Settings are Spring Boot properties. The library jar contains no `application.*` file: when embedded,
only the host's configuration and the in-code defaults below apply. The standalone application
(`DocumentAnalyzerApplication`) also loads `src/main/resources/document-analyzer.yml`, which sets the
standalone defaults (port, context path, model, system prompt). Override them with environment
variables, system properties, command-line arguments or an external YAML/properties file, following
Spring Boot's standard precedence. A copyable example is in
[`../application-example.yml`](../application-example.yml).

| Property | Required | Default (embedded / standalone) | Description | Evidence |
|---|---|---|---|---|
| `spring.ai.openai.api-key` | **Yes** for `generate`/`enrich` | — / empty | OpenAI API key. **Secret** | `document-analyzer.yml` |
| `spring.ai.openai.chat.options.model` | No | Spring AI default / `gpt-5-mini` | Chat model | `document-analyzer.yml` |
| `spring.ai.openai.chat.options.temperature` | No | Spring AI default / `1.0` | Sampling temperature | `document-analyzer.yml` |
| `spring.ai.openai.*` (other) | No | Spring AI defaults | For example `base-url` (OpenAI-compatible endpoint), timeouts and retry (`spring.ai.retry.*`) | Spring AI starter |
| `system-prompt` | No | unset / see `document-analyzer.yml` | When set, sent as the system message of every LLM request | `DocumentAnalyzerAutoConfiguration#documentContentProcessor` |
| `scraping.request-delay-ms` | No | `500` | Delay before every HTTP request and between rendered supplementary pages | `DocumentAnalyzerAutoConfiguration#documentAnalyzerService` |
| `document-analyzer.http.connect-timeout` | No | `10s` | Connect timeout of `HttpUriReader` | `DocumentAnalyzerProperties` |
| `document-analyzer.http.request-timeout` | No | `60s` | Time allowed for the response headers of each request | `DocumentAnalyzerProperties` |
| `document-analyzer.http.trust-all-certificates` | No | `false` | `true` accepts any server certificate chain for `HttpUriReader` fetches (hostname verification still applies). Use only if the analysed sites have broken chains | `DocumentAnalyzerProperties`, `HttpUriReader` |
| `document-analyzer.allow-private-addresses` | No | `false` | `true` allows URLs resolving to loopback, private, link-local, CGNAT, multicast or IPv6 unique-local addresses | `DocumentAnalyzerProperties`, `UrlGuard` |
| `server.port` | No | — / `8666` | Standalone mode only | `document-analyzer.yml` |
| `server.servlet.context-path` | No | — / `/api` | Standalone mode only | `document-analyzer.yml` |
| `server.compression.enabled` | No | — / `true` | Standalone mode only | `document-analyzer.yml` |
| `logging.level.eu.openaire` | No | — / `info` | Library log level | `document-analyzer.yml` |

Hard-coded limits, not configurable: 50 supplementary pages, 5 redirects per fetch, 60 s navigation
timeout, 5 s selector and network-idle waits, 4 render attempts, 5 LLM requests for valid JSON,
translation chunks of 400 items. (`HttpUriReader`, `WebPageContentExtractor`,
`LlmDocumentContentProcessor`)

## Embedding in a host application

- The auto-configuration creates `DocumentContentProcessor` and `DocumentAnalyzerService` unless the
  host defines its own. Chromium is launched on the first page render, so the host needs Playwright
  browsers only where pages are rendered (see [deployment.md](deployment.md#prerequisites)).
- `@EnableRetry` on the auto-configuration enables Spring Retry in the host as well.
- The host must set `system-prompt` itself if it wants one; the library supplies none.

## Environments

| Environment | Notes |
|---|---|
| Tests | `src/test/resources/application-test.yml`: `gpt-4.1-mini`, dummy API key, `eu.openaire: debug` |
| Local | Set `SPRING_AI_OPENAI_API_KEY`. Extraction works without a key; enrichment fails with 502 |
| Production | Set per deployment through environment variables or an external configuration. Not stored in the repository |

## Secrets

`spring.ai.openai.api-key` is the only secret. Supply it as `SPRING_AI_OPENAI_API_KEY` (or through
the host's secret store). Never commit it.
