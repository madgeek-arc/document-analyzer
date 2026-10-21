# Contributing

Contributions are welcome: bug reports, feature requests and pull requests.

## Reporting issues

Use [GitHub Issues](https://github.com/madgeek-arc/document-analyzer/issues) for bugs, feature requests and
questions. For bugs, include the version, the environment, the steps to reproduce, and the expected
and actual behaviour.

## Branching

- `main` is the integration and release branch.
- Work on short-lived branches named `<type>/<short-description>` (for example `fix/http-timeouts`,
  `docs/deployment`) and open a pull request against `main`.
- Without write access to the repository, fork it, create the branch in your fork and open the
  pull request from there.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/) are **required**. release-please derives
the next version and the `CHANGELOG.md` entries from them:

- `feat:` → minor bump, *Features*
- `fix:` → patch bump, *Bug Fixes*
- `perf:`, `deps:` and similar appear in their own sections. `docs:`, `test:`, `chore:`, `build:` don't
  trigger a release on their own
- `feat!:` / `BREAKING CHANGE:` footer → major bump

## Pull requests

- Describe the change and its motivation, and link the related issue.
- At least one maintainer review is required before merging.
- Update `README.md`/`docs/` when the API, configuration or crawling behaviour changes.
- Don't edit `CHANGELOG.md` or the version by hand. release-please maintains them.

## Required checks

- `./mvnw clean verify -Ddependency-check.skip=true` must pass locally.
- Jenkins runs the tests (with JaCoCo coverage) and the OWASP dependency check on every non-tag build.
  Address new critical or high findings.
- New behaviour needs unit tests next to the existing ones in `src/test/java`.

## Releases and versioning

- [Semantic Versioning](https://semver.org/).
- On every push to `main`, Jenkins runs `release-please release-pr`, which opens or updates a release
  PR. Merging it tags `<version>` (no `v` prefix), creates the GitHub release, and Jenkins deploys
  the artefact to the madgik Maven repository.
- `-SNAPSHOT` builds of `main` are deployed automatically.
