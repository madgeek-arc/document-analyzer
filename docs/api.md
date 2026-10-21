# Document Analyzer — API

These endpoints exist only when the project runs as a standalone application
(`DocumentAnalyzerApplication`). When embedded as a library, only the Java API is available. See
[integration.md](integration.md#provided-interfaces).

| Item | Value |
|---|---|
| Base URL | `http://<host>:8666/api` (`server.port`, `server.servlet.context-path` in `src/main/resources/document-analyzer.yml`) |
| Version | Path prefix `/v1` |
| Authentication | None |
| Swagger UI | `/api/swagger-ui.html` (springdoc) |
| OpenAPI JSON | `/api/v3/api-docs` (springdoc default path, generated at runtime, not version-controlled) |
| Compression | `server.compression.enabled=true` |

## Endpoint groups

| Method and path | Purpose | Source |
|---|---|---|
| `POST /v1/documents/extract?url=<url>[&topics=<kw>...]` | Return extracted `Content` (`HtmlContent` with `url`, `html`, `extraContent` for web pages; `metadata`, `text` for PDFs) | `DocumentAnalyzerController#extractContent` |
| `POST /v1/documents/enrich?url=<url>[&topics=<kw>...]` with JSON template body | Return the template filled in by the LLM | `DocumentAnalyzerController#enrichDocument` |

The README has request examples and the field-description convention for templates.

## Conventions and errors

- `url` is bound as `java.net.URL`. A malformed value gives HTTP 400 (Spring MVC binding).
- A URL that isn't `http`/`https` or resolves to a non-public address gives HTTP 400
  (`BlockedUrlException`).
- `enrich` gives HTTP 502 when the LLM returns no valid JSON or fails (`EnrichmentException`).

- Other fetch and parse failures surface as HTTP 500 with Spring Boot's default error body (there's no
  `@ControllerAdvice`).
- Calls are synchronous and can take minutes for whole-site extraction (up to 51 rendered pages, each
  with a request delay). Clients need generous read timeouts.
