# Contributing to beanquery

Thanks for taking the time to contribute.

## Getting started

```bash
git clone https://github.com/mszajner/beanquery.git
cd beanquery
./mvnw clean verify
```

Requires a full JDK 17–21 (`JAVA_HOME` must point at a JDK, not a JRE).

## Ground rules

- **Tests.** Every change ships with tests. Unit tests live next to the class;
  H2 integration tests are named `*IT` and run under Failsafe.
- **License headers.** `./mvnw license:format` adds the Apache-2.0 header to new
  files; `./mvnw verify` fails if one is missing.
- **Code style.** Match the surrounding code — 4-space indent, no wildcard
  imports, package-private by default.
- **Public API.** `io.github.mszajner.beanquery.core.{annotation,metadata,query,security}`
  and `io.github.mszajner.beanquery.starter` are the public surface; keep it minimal and
  documented. The `web` package is internal.
- **Backwards compatibility.** The `/metadata` and `/query` request/response
  shapes are a contract. The flat filter array must keep working.
- **Changelog.** Add a bullet under `## [Unreleased]` in `CHANGELOG.md`.

## Pull requests

1. Fork, branch from `main`.
2. Keep the PR focused; one concern per PR.
3. `./mvnw clean verify` must be green.
4. Fill in the PR template.

## Reporting security issues

Do **not** open a public issue. Email <mszajner@rexoft.pl>.

## Licensing

By contributing you agree that your contributions are licensed under the
Apache License 2.0.
