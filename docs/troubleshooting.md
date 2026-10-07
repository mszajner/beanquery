---
title: Troubleshooting
---

## Application startup problems

| Symptom | Cause | Fix |
|---|---|---|
| `QueryableMetadataException: Duplicate queryable entity name 'x'` | two entities with the same name | set a unique `@Queryable(name = ...)` |
| `... must declare nested sub-fields` | `@ManyToOne`/`@OneToOne` without `nested` | add `@QueryableField(nested = {"id", "name"})` |
| `... declares nested sub-fields but is not a @ManyToOne/@OneToOne` | `nested` on a scalar field | remove `nested` |
| `Nested field 'x' does not exist on ...` | a typo in `nested`, or the field is missing on the target class | fix the name |
| `... exceeds the maximum nesting depth of 1` | a dot in `nested` | one level only; use a view/`REFERENCE` |
| `... no ReferenceResolver bean has referenceName() == 'x'` | no resolver for a `@QueryableReference` | register a `@Component` with a matching `referenceName()` |
| `... has @QueryableReference but no @QueryableField` | no `@QueryableField` on the key field | add both annotations |
| `Duplicate queryable field` | a name collision (e.g. reference name = field name) | change `name` in `@QueryableReference` |
| no beanquery beans | the starter did not activate | check JPA (`EntityManagerFactory`), `beanquery.enabled`, the `beanquery-starter` dependency |

## Query errors (`400`)

| Message | Meaning |
|---|---|
| `select: must not be empty` | `select` is required and non-empty |
| `select: unknown field 'x'` | the field is not registered **or was hidden by an authorizer** |
| `field 'x' is not selectable/filterable/sortable` | a flag in `@QueryableField` forbids this operation |
| `operator GT is not allowed for field 'x'` | an operator outside the field's `operators` - check `/metadata` |
| `operator IS_NULL must not carry a value` | remove `value` |
| `operator BETWEEN requires an array of exactly 2 non-null values` | supply `[from, to]` |
| `operator IN requires a non-empty array value` | empty or not an array |
| `filter nesting exceeds the maximum depth of N` / `the request has N conditions` | filter-tree limits exceeded |
| `page.size: must be between 1 and 200` | page size too large/small |
| `filter on 'x': expected LocalDate but got ...` | a value in the wrong format (see [Filters]({{ '/filters/' | relative_url }})) |
| a JSON parsing error text | malformed JSON or a wrong field type in the body |

## `403` and `500`

- **`403 access_denied`** - a `QueryAuthorizer` returned `deny()` (or `null`). Check the logic and the security context
  (is the user authenticated at the time of the call?).
- **`500 Internal server error`** - details are **only in the server log** (`Unhandled beanquery error`).
  A typical cause is a `QueryAuthorizerConfigurationException` - a `MandatoryFilter` points at a nonexistent field or a
  disallowed operator. The log message names the entity, field and authorizer class.

## A query does not return the expected rows

- Check whether an authorizer adds a forced filter (e.g. tenant).
- A `REFERENCE` filter that matches nothing gives **0 rows**, not "no filter".
- `EQ` on text is case-sensitive according to the database collation; use `ILIKE` for searching.
- For `LIKE`/`ILIKE` the value is matched as *contains*; `%` and `_` are not wildcards.
- Check the SQL logs (`logging.level.org.hibernate.SQL=debug`) to see the executed query.

## Performance

- Add indexes on columns clients filter and sort by - beanquery does not guess indexes.
- Lower `beanquery.max-page-size`, `max-filter-conditions` and `max-filter-depth` if the UI does not need more.
- Expose only sensible fields via `@QueryableField` (fewer fields = fewer possible query patterns).
- `resolve` in a `ReferenceResolver` should be a single batch query for the whole page.

## Reporting bugs

Use the [issue template]({{ site.repo_url }}/issues/new/choose). Report security vulnerabilities
according to [SECURITY.md]({{ site.repo_url }}/blob/main/SECURITY.md), not publicly.
