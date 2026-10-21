# Document Analyzer — Development

## Tools

| Tool | Version | Evidence |
|---|---|---|
| JDK | 21+ | `pom.xml` `java.version` |
| Maven | wrapper (`./mvnw`) | `.mvn/wrapper` |
| Playwright Chromium | matching Playwright 1.52.0 | `pom.xml`, `Jenkinsfile` image tag |
| Node.js | only for release-please in CI | `Jenkinsfile` |

## Commands

```bash
./mvnw clean verify -Ddependency-check.skip=true   # tests + JaCoCo report (target/site/jacoco)
./mvnw dependency-check:check -DnvdApiKey=<key>    # OWASP scan (slow, needs an NVD API key)
SPRING_AI_OPENAI_API_KEY=<key> java -jar target/document-analyzer-<version>-exec.jar   # standalone on :8666/api
SPRING_AI_OPENAI_API_KEY=<key> ./mvnw spring-boot:run   # same, from source
./mvnw install -DskipTests                         # install the SNAPSHOT for a local host app
```

Verified: `./mvnw verify -Ddependency-check.skip=true` passes (164 tests). Without
`-Ddependency-check.skip=true`, `verify` also runs the OWASP check, which downloads the NVD database.

## Local setup notes

- Playwright downloads Chromium on first use. On Linux, missing system libraries cause launch errors.
  Install them with Playwright's CLI (`install --with-deps chromium`), or develop inside the
  `mcr.microsoft.com/playwright/java:v1.52.0-jammy` image.
- Extraction works without an OpenAI key. Enrichment needs one.
- To analyse a local test site (for example on `localhost`), set
  `document-analyzer.allow-private-addresses=true`.
- Set `logging.level.eu.openaire=debug` and
  `logging.level.org.springframework.ai.chat.client.advisor=debug` to see crawl decisions and prompts.

## Conventions

- Conventional Commits drive release-please versioning and the changelog (see
  [CONTRIBUTING.md](../CONTRIBUTING.md)).
- No formatter or linter is configured. Sources carry an Apache 2.0 header.
- Extending URL filtering: implement `SupplementaryUrlFilter`, add a `SupplementaryUrlFilterMethod`
  constant, and register the filter in `WebPageContentExtractor`'s `supplementaryUrlFilters` map.
