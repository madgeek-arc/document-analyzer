# Changelog

## [2.0.0](https://github.com/madgeek-arc/document-analyzer/compare/1.0.0...2.0.0) (2026-06-17)


### ⚠ BREAKING CHANGES

* Cannot run as service - only as a library
* remove "/api" prefix from controller

### Features

* Add rate limiting protection and fix @Retryable ([cb6e398](https://github.com/madgeek-arc/document-analyzer/commit/cb6e398a6573c220707879f792060c025deb0a28))
* Adds Playwright-based rendering to WebPageContentExtractor ([e3f39f3](https://github.com/madgeek-arc/document-analyzer/commit/e3f39f3b9e64758f3294b68d729f21fff1d4168f))
* Adds springdoc openapi dependency ([c182420](https://github.com/madgeek-arc/document-analyzer/commit/c1824203e2d5cbf8129ee0c6801aaa553d112913))
* Changes return type of UriReader to return uri alongside data. ([e2f38da](https://github.com/madgeek-arc/document-analyzer/commit/e2f38dad111e92aa6c71769dedd8405a2b0f3d53))
* Creates controller method extracting url data ([5156e60](https://github.com/madgeek-arc/document-analyzer/commit/5156e6061f8dc4d0a2ac2eee0fe60453e5642999))
* extract most informative paragraphs from document ([4f22348](https://github.com/madgeek-arc/document-analyzer/commit/4f22348b547a93d73597466a17b127ffe38ba9c9))
* extract relevant urls from the main page to use for scraping alongside the sitemap urls ([56b389d](https://github.com/madgeek-arc/document-analyzer/commit/56b389dcfa85038a6d6cb0777d5368215bbb2a58))
* Handle HTTP 202 responses by falling back to Playwright ([88cc797](https://github.com/madgeek-arc/document-analyzer/commit/88cc79761717fd1acaea35e99bdd41a350b840fd))
* Implement copy constructors ([1833675](https://github.com/madgeek-arc/document-analyzer/commit/1833675c34d3c63bdd3a0f4493c553bf0584fe6c))
* Implement path-relevance filtering for sitemap URLs ([7f8a19d](https://github.com/madgeek-arc/document-analyzer/commit/7f8a19d3e999e82f29abe2c80914115d7f0ea714))
* Implement whole-page scaping. ([3ad40c7](https://github.com/madgeek-arc/document-analyzer/commit/3ad40c77934bab115984814e0aeb27d21f0ffae2))
* **scraping:** add extensible topic-based supplementary URL filtering ([df52327](https://github.com/madgeek-arc/document-analyzer/commit/df523272451ef3ba642dea64b59d31a26abd7e96))


### Bug Fixes

* applies configuration on spring-ai static ObjectMapper to stop Duration serializations/deserialization issues ([25aa483](https://github.com/madgeek-arc/document-analyzer/commit/25aa4835b14a03ad19f9059689e3f5007b81190f))
* do not skip supplementary pages if they are too many, use the most relevant instead ([65f0d1a](https://github.com/madgeek-arc/document-analyzer/commit/65f0d1ac5e42e7dc0bff210ef25ecdca1b4606be))
* Error while searching for English equivalent page was breaking the loop. Refactored url lists to sets ([d2d9a81](https://github.com/madgeek-arc/document-analyzer/commit/d2d9a81956c27b7f872d6212bbbb6aead4614b2f))
* Excludes document urls when scraping a website ([f8911cf](https://github.com/madgeek-arc/document-analyzer/commit/f8911cf1472b343b40daf630d10a2505e6cc0d25))
* follow redirects ([e58c252](https://github.com/madgeek-arc/document-analyzer/commit/e58c252394592b5c19033de15d1fc1b34bb7e963))
* Improves performance and quality when sitemap contains too many pages by filtering out irrelevant pages. ([f19d90b](https://github.com/madgeek-arc/document-analyzer/commit/f19d90ba0e84f3c463e4949471a1fbc6cdad3d8b))
* Increase page load and network timeouts and uses custom user agent and headers to bypass bot detection ([a192ca7](https://github.com/madgeek-arc/document-analyzer/commit/a192ca7e132c84d9a4f9487e0d917765c5680385))
* increase resilience on failures ([f5ea8c0](https://github.com/madgeek-arc/document-analyzer/commit/f5ea8c0f3c5094e85cb9f88fb92d8d69824f14a1))
* make uri reader more resilient ([2377343](https://github.com/madgeek-arc/document-analyzer/commit/2377343b78f0adc0206613d09a687dfef12ecd57))
* serialize shared Playwright browser access, respect robots.txt and try to fix tls san missmatch errors ([94e3ea4](https://github.com/madgeek-arc/document-analyzer/commit/94e3ea4babb222e8ba3c3c75fb9f05fc8996e921))


### Performance Improvements

* Keep English versions of the website's sup-pages. Reduces number of pages to scrape ([e7ab1f7](https://github.com/madgeek-arc/document-analyzer/commit/e7ab1f74ea67d535bbe55299571a998a34d080e8))


### Dependencies

* Bumps spring-ai version ([922b829](https://github.com/madgeek-arc/document-analyzer/commit/922b8295b7b4f53007f60169d1167d3606bb1e90))


### Documentation

* Updates documentation ([11edb38](https://github.com/madgeek-arc/document-analyzer/commit/11edb38d320042a4abadbaa8b8cb4fe7ad0e33f4))


### Code Refactoring

* Cannot run as service - only as a library ([be2c41a](https://github.com/madgeek-arc/document-analyzer/commit/be2c41ac7da8cf32057a27ec8d7360cb4d465fa8))
* remove "/api" prefix from controller ([bb4b391](https://github.com/madgeek-arc/document-analyzer/commit/bb4b3915514a30a382b312f1a574e3cbe6c0ffc2))

## Changelog

##  (2025-12-24)

### Features

* extract most informative paragraphs from document ([e8384b8](https://github.com/madgeek-arc/resources-registry/commit/e8384b8bb2fbf497011c7d3d44fca20967cf1ea8))

### Bug Fixes

* applies configuration on spring-ai static ObjectMapper to stop Duration serializations/deserialization issues ([14a718f](https://github.com/madgeek-arc/resources-registry/commit/14a718fdcaf24a21d2c4a96b4b7a2dc018acaa2e))
* follow redirects ([ca83611](https://github.com/madgeek-arc/resources-registry/commit/ca83611c96df3b63e84833781b7219612da1acff))
* increase resilience on failures ([17fb711](https://github.com/madgeek-arc/resources-registry/commit/17fb71152fb420dc726ce2d789ae2870f1d95059))

### Code Refactoring

* Cannot run as service - only as a library ([7e0d51c](https://github.com/madgeek-arc/resources-registry/commit/7e0d51cc373fe395b21b1e60836598f57b77effe))
* remove "/api" prefix from controller ([b2d929e](https://github.com/madgeek-arc/resources-registry/commit/b2d929ec787d41fe378829f79005d32ee58cef8a))
