# Document Analyzer

Spring Boot library/service for extracting and enriching content from URLs. 
Currently, it supports HTML pages and PDF documents.
It fetches the document using the provided URL, extracts its text, and optionally 
uses an OpenAI model to populate a JSON template with structured data from that content.

## Requirements

- Java 21
- Maven 3.6+ (or use the included `./mvnw` wrapper)
- An OpenAI API key (optional -- used only for populating a JSON template with structured data)

## Build

```bash
./mvnw package -DskipTests
```

<!-- x-release-please-start-version -->
The executable JAR is produced at `target/document-analyzer-2.0.0`.
<!-- x-release-please-end -->


## Configuration

All settings are in `src/main/resources/application.yml`. The minimum required override at runtime is the OpenAI API key.

| Property | Default | Description |
|---|---|---|
| `spring.ai.openai.api-key` | _(empty)_ | **Required.** OpenAI API key |
| `spring.ai.openai.chat.options.model` | `gpt-5-mini` | OpenAI model |
| `spring.ai.openai.chat.options.temperature` | `1.0` | Sampling temperature |
| `server.port` | `8666` | HTTP port |
| `server.servlet.context-path` | `/api` | Context path |
| `scraping.request-delay-ms` | `500` | Delay between HTTP requests to the same host |
| `system-prompt` | _(see yml)_ | System prompt sent to the LLM on every enrich call |

Override as environment variables, system properties or by providing a custom .properties/.yaml file.

## Run

<!-- x-release-please-start-version -->
```bash
java -Dspring.ai.openai.api-key=<your-openai-key> -jar target/document-analyzer-2.0.0
```
<!-- x-release-please-end -->

The API is available at `http://localhost:8666/api`.
The Swagger UI is at `http://localhost:8666/api/swagger-ui.html`.

## API

### Extract — `POST /api/v1/documents/extract`

Downloads the URL and returns its extracted text content. Detects PDF vs HTML automatically.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `url` | query | yes | URL of the document or web page |
| `topics` | query (repeatable) | no | Keyword hints that filter which supplementary pages are fetched from the site. Pass multiple times for multiple keywords. Omit to fetch all linked pages. |

**Response** — `Content` JSON object:

```json
{
  "metadata": "...",
  "text": "..."
}
```

HTML responses are `HtmlContent` (extends `Content`) and include `url`, `html`, and an `extraContent` map of supplementary pages.

**Examples:**

```bash
# Basic extraction
curl -X POST "http://localhost:8666/api/v1/documents/extract?url=https://example.org"

# With topic filtering — only follow links related to "contact" or "about"
curl -X POST "http://localhost:8666/api/v1/documents/extract?url=https://example.org&topics=contact&topics=about"

# PDF document
curl -X POST "http://localhost:8666/api/v1/documents/extract?url=https://example.org/paper.pdf"
```

### Enrich — `POST /api/v1/documents/enrich`

Fetches the URL, extracts its content, then uses the LLM to fill in a JSON template with data from that content.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `url` | query | yes | URL of the document or web page |
| `topics` | query (repeatable) | no | Keyword hints for supplementary page filtering (same as `/extract`) |
| _(body)_ | JSON | yes | Template object; the LLM populates its fields from the document |

**Response** — the template filled in by the LLM.

**Examples:**

```bash
# Basic enrichment
curl -X POST "http://localhost:8666/api/v1/documents/enrich?url=https://example.org/about" \
  -H "Content-Type: application/json" \
  -d '{"name": null, "description": null, "contactEmail": null}'
```

For more precise extraction, populate the template values with natural-language descriptions of what each field should contain. The LLM uses these as per-field instructions:

```bash
# Advanced enrichment — include descriptions in values to guide the LLM per field
curl -X POST "http://localhost:8666/api/v1/documents/enrich?url=https://example.org/about" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Official name of the organization",
    "description": "Short paragraph summarizing the organization mission and main activities",
    "foundingYear": "Year the organization was established, as a number",
    "contactEmail": "Primary public contact email address",
    "websiteUrl": "Canonical URL of the organization homepage"
  }'

# Advanced enrichment with topic filtering
curl -X POST "http://localhost:8666/api/v1/documents/enrich?url=https://example.org&topics=about&topics=contact" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Official name of the organization",
    "description": "Short paragraph summarizing the organization mission and main activities",
    "foundingYear": "Year the organization was established, as a number",
    "contactEmail": "Primary public contact email address",
    "websiteUrl": "Canonical URL of the organization homepage"
  }'
```

## Import as a library

The artifact is published to the madgik Maven repository. Add to your `pom.xml`:

<!-- x-release-please-start-version -->
```xml
<repositories>
  <repository>
    <id>madgik-snapshots</id>
    <url>https://repo.madgik.di.uoa.gr/content/repositories/snapshots</url>
  </repository>
  <repository>
    <id>madgik-releases</id>
    <url>https://repo.madgik.di.uoa.gr/content/repositories/releases</url>
  </repository>
</repositories>

<dependency>
  <groupId>eu.openaire</groupId>
  <artifactId>document-analyzer</artifactId>
  <version>2.0.0</version>
</dependency>
```
<!-- x-release-please-end -->

Spring Boot auto-configuration (`DocumentAnalyzerAutoConfiguration`) wires `DocumentAnalyzerService` automatically when the dependency is on the classpath and `system-prompt` is set. Inject the service directly:

```java
@Autowired
DocumentAnalyzerService documentAnalyzerService;

// extract
Content content = documentAnalyzerService.read(uri);

// extract + enrich
JsonNode result = documentAnalyzerService.generate(uri, templateNode);
```

## License

Apache License 2.0 — see [LICENSE](LICENSE).
