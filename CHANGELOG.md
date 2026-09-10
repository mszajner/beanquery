# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `@QueryableReference` + the `ReferenceResolver` SPI: expose fields of another
  Spring Modulith module's entity via a runtime resolver instead of a JPA
  association. Reference sub-fields are selectable and filterable (the resolver
  translates a filter clause to a local `id IN (…)`), never sortable. New
  property `beanquery.max-reference-filter-ids` (default 1000) caps that id set.

## [0.1.0] - 2026-09-09

### Added
- `@Queryable` / `@QueryableField` annotations and the metadata registry.
- `GET /api/bq/{entity}/metadata` and `POST /api/bq/{entity}/query`.
- JPA Criteria query engine with depth-1 `LEFT JOIN`s, deduplicated per query.
- AND/OR filter tree (`filters` accepts the legacy flat array or a nested
  `{ "logic", "children" }` object), with configurable depth / condition limits.
- Filter-value conversion (ISO-8601 dates, enums by name, `BigDecimal`, `UUID`).
- `QueryAuthorizer` hook: deny a call (`403`), force `AND` predicates over the
  whole request (row + count), hide fields per call.
- Spring Boot starter with auto-configuration and `beanquery.*` properties.
- `beanquery-demo` module (H2) with `demo.http` / `demo.curl.sh` examples.

[Unreleased]: https://github.com/mszajner/beanquery/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/mszajner/beanquery/releases/tag/v0.1.0
