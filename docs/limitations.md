---
title: Limitations and roadmap
---

Below is what the library **does not do today** - important when judging whether it fits your case.
Version `0.x` is not yet stabilised; the REST contract is nevertheless treated as fixed
(in particular, the flat filter array must keep working).

## Limitations of the current version

### Data model

| Limitation | Details | Workaround |
|---|---|---|
| Join depth 1 | `customer.name` yes, `customer.address.city` no; fields must be declared in `nested` | an intermediate field, an SQL view (`@Subselect`) |
| To-one relations only | `@OneToMany` / `@ManyToMany` are not exposed | a separate `@Queryable` entity on the "many" side filtered by foreign key; an SQL view |
| No aggregation | no `GROUP BY`, `COUNT`, `SUM`, `DISTINCT` or projections beyond selected scalar fields | an SQL view with the aggregate as an `@Immutable` entity |
| No computed fields/aliases | a field maps 1:1 to an entity property | a computed column in a view / a Hibernate `@Formula` on an entity field |
| Embedded types (`@Embedded`) | no dedicated support | flatten in a view or add extra fields |
| Types without default operators | `UUID`, `OffsetDateTime`, `ZonedDateTime`, etc. | explicit `operators` |

### Querying

| Limitation | Details |
|---|---|
| Fixed operator set | 13 built in; `LIKE`/`ILIKE` are always "contains" - no "starts with", "ends with", regex or full-text |
| No group negation | there is no `NOT` node; negation only at operator level (`NE`, `NOT_IN`, `IS_NOT_NULL`) |
| No field-to-field filters | you cannot compare two columns (`price > cost`) |
| Offset pagination | every request also runs a `COUNT`; no keyset (cursor) pagination or option to skip counting - costly on very large tables |
| `select` is required | there is no "all fields"; the client must list them |
| Forced ordering by `id` | always appended last (stability), cannot be disabled |
| Case in `EQ`/`IN` | depends on the database collation; only `ILIKE` is explicitly case-insensitive |

### Cross-module references

- `@QueryableReference` sub-fields are **not sortable** and have depth 1.
- A filter is translated to `id IN (...)` with the `max-reference-filter-ids` limit - very broad filters must be narrowed.
- The resolver is called synchronously during the query; there is no built-in cache or handling of remote-service failures.

### Platform and integration

- **Read-only** - there is no write path of any kind.
- Spring MVC (servlet). No WebFlux support.
- Built and tested on Spring Boot **4.1.x**, Java 17+; older Spring Boot versions are not supported.
- No built-in Spring Security integration - you supply the identity in a `QueryAuthorizer`.
- No generated OpenAPI specification for the endpoints (the request shape is fixed and `/metadata` describes the fields).
- The starter assumes a single `EntityManagerFactory`; a setup with several persistence units is neither documented nor tested.
- Errors are plain strings (`errors: [string]`), with no stable machine-readable codes.
- No ready-made client/UI (see [Client integration]({{ '/client/' | relative_url }})).

## What may still be missing

Ideas that follow from the gaps above. **This is not a commitment or an approved roadmap** - just directions that make
sense if demand appears. Report a need in the [issues]({{ site.repo_url }}/issues).

1. **Aggregation and grouping** - `COUNT`/`SUM`/`AVG`, `GROUP BY` with its own function whitelist.
2. **To-many relations** - "exists an element that..." filters (`EXISTS`) without fetching collections.
3. **Deeper joins** - controlled depth > 1 with an explicit path whitelist.
4. **Sorting by `REFERENCE`** - e.g. via an additional resolver contract returning ordered ids.
5. **Cursor pagination** and a mode without `COUNT`.
6. **More operators** - `STARTS_WITH`, `ENDS_WITH`, `NOT`, field-to-field comparison, host-registered custom operators.
7. **Declarative computed fields** (server-side Criteria expressions).
8. **Stable error codes** and an RFC 9457 format (`application/problem+json`).
9. **OpenAPI / JSON Schema** generated from the metadata, together with a TypeScript client.
10. **Ready-made Spring Security integration** (e.g. annotations/SpEL on fields or entities instead of a hand-written authorizer).
11. **Metrics and audit** - query count/latency (Micrometer), a log of who asked what.
12. **Export** of results (CSV/XLSX) with the same query.
13. **Metadata caching** and support for multiple persistence units.
14. **WebFlux / R2DBC support** and older Spring Boot versions.

## When beanquery is *not* a good fit

- you need to write data or perform domain operations - use your own endpoints,
- you need complex reports with aggregations and many joins - consider SQL views or a dedicated reporting/BI layer,
- public clients should flexibly fetch arbitrary object graphs - GraphQL is the better tool,
- you need a guaranteed stable API for years - the library is at `0.x`.
