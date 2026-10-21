# Document Analyzer — Deployment

The repository defines how the **library** is built and published. It contains no Dockerfile,
compose file or Kubernetes manifest for the standalone service. Production deployment isn't defined
in this repository. Host applications embed the library. The standalone service, if used, can run as
an executable jar managed as a system service, or as a container started with Docker Compose or
deployed on Kubernetes.

## Prerequisites

- JDK 21 and Maven (wrapper included).
- **Playwright Chromium** wherever pages are rendered (the browser is launched on the first render,
  not at startup). On first use, Playwright downloads browser binaries (it needs outbound
  internet access and the OS libraries Chromium depends on), or uses pre-installed browsers, for
  example the `mcr.microsoft.com/playwright/java:v1.52.0-jammy` image used in CI.
  (`Jenkinsfile`, `WebPageContentExtractor#browser`). Playwright downloads the browsers to
  `PLAYWRIGHT_BROWSERS_PATH` (default `~/.cache/ms-playwright`) unless
  `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` is set.
- Outbound HTTPS to the websites being analysed and to the OpenAI API.

## Library publishing (CI)

`Jenkinsfile` stages:

| Stage | When | What |
|---|---|---|
| Determine Version | always | `./mvnw help:evaluate -Dexpression=project.version` |
| Verify → Test | non-tag builds | `./mvnw -B verify -Ddependency-check.skip=true` inside the Playwright Java image. Publishes JUnit and JaCoCo reports. Test failures mark the build unstable |
| Verify → Dependency Check | non-tag builds | OWASP `dependency-check:check` with an NVD API key from Jenkins credentials |
| Deploy Artifacts | `-SNAPSHOT` versions or tag builds | `./mvnw deploy` to the madgik repository (settings from Jenkins config file `madgik-settings`) |
| Handle Releases | `main`, not a PR | `release-please github-release` and `release-pr` (GitHub token from Jenkins credentials) |

Releases are made by merging the release-please PR, which bumps `pom.xml`, `README.md` and
`CHANGELOG.md` and creates the tag `<version>` (no `v` prefix, per `release-please-config.json`).

Artefact: `eu.openaire:document-analyzer:<version>` (plain jar). The executable `*-exec.jar` isn't
attached, so it isn't deployed.

## Standalone service

`./mvnw package` also produces `target/document-analyzer-<version>-exec.jar` (`spring-boot-maven-plugin`,
`exec` classifier). It loads `document-analyzer.yml` in addition to the usual `application.*` files.
(started locally: `/api/v3/api-docs` returned 200 on port 8666)

```bash
SPRING_AI_OPENAI_API_KEY=<openai-api-key> java -jar target/document-analyzer-<version>-exec.jar
# or from source
SPRING_AI_OPENAI_API_KEY=<openai-api-key> ./mvnw spring-boot:run
```

| Aspect | Value |
|---|---|
| Port | `8666` |
| Context path | `/api` |
| Volumes | none required. Playwright's browser cache (by default under the user's home) can be persisted to avoid re-downloads |
| Dependent services | OpenAI API, the target websites |
| Database / migrations | none |

## Post-deployment verification (standalone)

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://<host>:8666/api/v3/api-docs     # expect 200
curl -X POST "http://<host>:8666/api/v1/documents/extract?url=https://example.org"
```

## Rollback

The service is stateless, so redeploy the previous version (library: pin the previous version in the
host's `pom.xml`).
