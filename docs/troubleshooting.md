# Document Analyzer — Troubleshooting

| Problem | Possible cause | What to check | Resolution |
|---|---|---|---|
| Page rendering fails with a Playwright error | Playwright can't download or launch Chromium (no internet, missing OS libraries) | Stack trace of the first render | Pre-install browsers (Playwright image or `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install --with-deps chromium"`), or allow the download |
| HTTP 400 / `BlockedUrlException: URL resolves to a non-public address` | The URL (or a redirect) points to a loopback, private or link-local address | The URL and its DNS resolution | Expected for internal URLs. Set `document-analyzer.allow-private-addresses=true` only if internal sites must be analysed |
| `SSLHandshakeException` / `PKIX path building failed` on specific sites | The site's certificate chain is incomplete, self-signed or expired | `openssl s_client -connect <host>:443 -showcerts` | Fix on the site, or set `document-analyzer.http.trust-all-certificates=true` |
| TLS errors mentioning the hostname | Certificate SAN mismatch with no usable fallback | `WARN Retrying ... due to TLS hostname mismatch`, `Certificate for ... is valid for DNS names` | Use the canonical host in the URL |
| `enrich` returns HTTP 502 | OpenAI call failed (missing key, quota) or invalid JSON 5 times | `ERROR Could not generate document metadata` from `LlmDocumentContentProcessor` | Set `spring.ai.openai.api-key`. Check the model name and quota |
| HTTP 500 `Could not parse sitemap.` or `RuntimeException` | Fetch of the main URL failed (403, non-200, rate limit, timeout, TLS) | `HttpUriReader` log line and the cause | Retry later for 429/503. Check the URL is reachable from the server |
| `HttpTimeoutException` | The site didn't answer within `document-analyzer.http.request-timeout` | `Reading url` line | Raise the timeout if the site is just slow |
| Requests take minutes or time out on the client | Whole-site crawl of up to 51 pages, serialised on one browser, with render retries | `Limited supplementary URLs ...`, `Rendering ... failed (attempt n/4)`, `Reading url` lines | Pass `topics` to narrow pages. Lower `scraping.request-delay-ms`. Raise client timeouts |
| Only the landing page is extracted | No sitemap and no same-domain links, or robots rules disallow them | `Sitemap not found`, `robots.txt disallows` (DEBUG) | Expected behaviour |
| Wrong language content | English alternate not advertised by the site | Page `hreflang` links | Pass the English URL directly |
