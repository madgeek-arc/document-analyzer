# Security Policy

## Supported Versions

| Version | Supported |
|---|---|
| 1.x (latest minor) | :white_check_mark: |
| Older 1.x minors | :x: |

Fixes are released as a new version of each supported line; upgrade to it.

## Reporting a Vulnerability

Report vulnerabilities privately through GitHub: open the repository's **Security** tab and choose
**Report a vulnerability** ([direct link](https://github.com/madgeek-arc/document-analyzer/security/advisories/new)).
Don't report them in public issues, pull requests or discussions.

Include:

- the affected version or commit;
- steps to reproduce, or a proof of concept;
- the impact you expect.

The maintainers follow up in the private advisory, fix the issue in the supported versions, and publish
the advisory once a fixed release is available.

## Scope

This repository covers the library and its standalone REST mode. The standalone endpoints have no
authentication, so a deployment must restrict who can reach them. Server-side fetching of URLs
(address filtering, redirects, TLS validation) is in scope.

[docs/security-model.md](docs/security-model.md) describes the component's security model and the
settings a deployment should use.
