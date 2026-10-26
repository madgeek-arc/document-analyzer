# Document Analyzer — Security model

## Authentication and authorisation

None. The standalone REST endpoints and Swagger UI are open to anyone who can reach the port. When
embedded, access control is the host's responsibility. (no Spring Security dependency)

## Outbound requests

The component fetches caller-supplied URLs server-side, with both `HttpClient` and Chromium, and also
follows redirects, links, sitemap entries and host-fallback rewrites. `UrlGuard` limits all of these:
(`UrlGuard`, `HttpUriReader`, `WebPageContentExtractor`, `UrlGuardTest`,
`HttpUriReaderLocalServerTest`)

- Only `http` and `https` URLs are accepted.
- The host must not resolve to a loopback, private (RFC 1918), link-local (including
  `169.254.169.254` metadata endpoints), CGNAT (`100.64.0.0/10`), multicast, wildcard or IPv6
  unique-local address. Blocked URLs raise `BlockedUrlException` (HTTP 400).
- `HttpUriReader` follows redirects itself (at most 5) and checks every hop.
- Every request the headless browser makes (page, redirects, sub-resources, scripts' fetches) is routed
  through the guard and aborted when blocked; a navigation ending on a blocked URL fails.

`document-analyzer.allow-private-addresses=true` disables the address check (the scheme check stays).

Public URLs are fetched on behalf of any caller, so restrict egress at the network level and accept
URLs only from authenticated callers.

## TLS

`HttpUriReader` validates server certificates with the JVM's default trust store. Setting
`document-analyzer.http.trust-all-certificates=true` installs a trust-all `X509TrustManager` for its
fetches (sitemaps, robots.txt, PDFs, the first fetch of every URL); hostname verification remains, but
such connections are open to interception by anyone able to present any certificate. A permissive
context is otherwise used only to read the certificate's SAN names after a hostname mismatch, without
exchanging content. (`HttpUriReader`). Chromium (Playwright) validates certificates,
because the browser contexts don't set `ignoreHTTPSErrors`.

Chromium launches with `--no-sandbox --disable-setuid-sandbox`, which weakens isolation between
rendered pages and the host process. Run it in a container with minimal privileges.
(`WebPageContentExtractor` constructor)

## Third-party data sharing

`generate` sends the extracted document text (including supplementary pages) and the template to the
OpenAI API. Don't analyse documents containing personal or confidential data unless that data flow
is covered by the deployment's data-processing agreements. (`LlmDocumentContentProcessor`)

## Secrets

- `spring.ai.openai.api-key`: inject it through the environment or a secret store. The repository
  default is empty. The test key `1234...` in `application-test.yml` is a dummy.
- CI uses Jenkins credentials `nvd-api-key` and `jenkins-github-pat` and the config file
  `madgik-settings`. None of them is in the repository. (`Jenkinsfile`)

## Logging of sensitive content

Generated JSON is logged at `DEBUG` (`DocumentAnalyzerService#generate`). With DEBUG enabled for
Spring AI's `SimpleLoggerAdvisor`, full prompts and responses are logged. Keep DEBUG off for these
loggers in production.

## Crawling etiquette

`robots.txt` (as `DocumentAnalyzerBot`), `X-Robots-Tag` and meta-robots `noindex` are honoured for
supplementary pages. If `robots.txt` can't be fetched, everything is allowed. The requests themselves
use a desktop Chrome `User-Agent` rather than the bot name. (`RobotsCache`,
`HttpUriReader#read`)

## Dependency scanning

The OWASP dependency-check runs in CI (`failBuildOnCVSS=11`, so it never fails the build. The build
is marked unstable at ≥1 critical or ≥3 high findings). (`Jenkinsfile`)
