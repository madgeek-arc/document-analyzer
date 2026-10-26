# Document Analyzer — Testing

Unit and slice tests live under `src/test/java`, mirroring the main packages.

| Area | Test classes |
|---|---|
| Application context | `DocumentAnalyzerApplicationTests` |
| Auto-configuration | `autoconfigure/DocumentAnalyzerAutoConfigurationTest` (system prompt, bean back-off, `document-analyzer.*` binding) |
| REST layer | `analyze/controller/DocumentAnalyzerControllerTest` |
| Orchestration | `analyze/service/DocumentAnalyzerServiceTest` |
| Models | `common/model/ContentTest`, `HtmlContentTest`, `PdfContentTest` |
| LLM prompting and JSON handling | `enrich/service/ContentMetadataRequestBuilderTest`, `LlmDocumentContentProcessorTest` |
| Fetching, robots and filtering | `extract/service/HttpUriReaderTest`, `FileUriReaderTest`, `RobotsCacheTest`, `SimpleSupplementaryUrlFilterTest`, `WebPageContentExtractorFilterTest`, `ExtractServiceExceptionsTest` |
| URL guard, redirects, timeouts | `extract/service/UrlGuardTest`, `HttpUriReaderLocalServerTest` (in-JVM HTTP server on loopback) |
| PDF | `extract/service/PdfContentExtractorTest` |
| Utilities | `utils/DiffUtilsTest`, `RegexUtilsTest` |

Test configuration: `src/test/resources/application-test.yml` (dummy OpenAI key, `gpt-4.1-mini`, debug
logging). Surefire attaches Mockito as a Java agent (`-javaagent:${org.mockito:mockito-core:jar}`,
resolved by `maven-dependency-plugin:properties`). (`pom.xml`)

## Running

```bash
./mvnw test
./mvnw verify -Ddependency-check.skip=true        # + JaCoCo report at target/site/jacoco/
./mvnw test -Dtest=HttpUriReaderTest
```

Result: 164 tests, 0 failures, 0 skipped. No OpenAI access is required (the LLM is mocked), and no
browser is launched: rendering isn't exercised by the tests.

## CI

Jenkins runs `verify` in the Playwright Java image, publishes JUnit and JaCoCo coverage, and runs the
OWASP dependency check in parallel. Test failures mark the build *unstable*, not failed
(`-Dmaven.test.failure.ignore=true`). (`Jenkinsfile`)

## Not covered

- End-to-end crawling of real sites, and real Chromium rendering (including the browser request guard
  and the render retry), in CI.
