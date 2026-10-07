---
title: beanquery
description: Whitelist-first REST query API over JPA entities for Spring Boot
---

<p style="text-align:center; margin: 24px 0 8px">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="{{ '/assets/logo/beanquery-logo-dark.svg' | relative_url }}">
    <img src="{{ '/assets/logo/beanquery-logo.svg' | relative_url }}" alt="beanquery" width="380">
  </picture>
</p>

# beanquery

**beanquery** is a Spring Boot library that exposes selected JPA entities through a ready-made REST API for
**dynamic querying**: column selection, `AND`/`OR` filters, sorting and pagination - with no controllers,
repositories or hand-written queries for each view.

You annotate the entities and fields you want to expose. beanquery provides two endpoints:

- `GET  /api/bq/{entity}/metadata` - the fields a client may use (types, allowed operators, enum values),
- `POST /api/bq/{entity}/query` - body `{ select, filters, sort, page }`, response `{ rows, page }`.

The JSON request is turned into a safe **JPA Criteria API** query. A field is reachable
**only if it is explicitly annotated** - request strings never reach JPQL/SQL.

## What it is for

It fits if you are building:

<div class="cards">
  <div class="card"><strong>Filterable tables and lists</strong>Admin panels and data grids with user-selectable columns.</div>
  <div class="card"><strong>Query builders / reports</strong>A "build your own filter" screen driven by the <code>/metadata</code> endpoint.</div>
  <div class="card"><strong>Internal data APIs</strong>One generic read-only API instead of dozens of similar <code>GET /...?filter=</code> endpoints.</div>
  <div class="card"><strong>Multi-module applications</strong>Fields from other Spring Modulith modules via <code>ReferenceResolver</code> - no JPA join.</div>
</div>

It is not: a write layer (it is strictly **read-only**), nor a replacement for GraphQL or Spring Data REST
(no to-many relations, aggregations or mutations - see [Limitations]({{ '/limitations/' | relative_url }})).

## Features at a glance

| Area | What you get |
|---|---|
| **Whitelist** | Only `@QueryableField` fields are visible; separate `selectable` / `filterable` / `sortable` flags |
| **Filters** | `AND`/`OR` tree of arbitrary nesting (with limits), 13 operators, the legacy flat array still works |
| **Types** | `String`, numbers, `BigDecimal`, `LocalDate`, `LocalDateTime`, `Instant`, `boolean`, enums, `UUID` - values converted server-side |
| **Relations** | Depth-1 `@ManyToOne`/`@OneToOne` (`customer.name`) via `LEFT JOIN`, de-duplicated |
| **Module boundaries** | `@QueryableReference` + `ReferenceResolver` - fields from another module/service, no JPA join |
| **Authorization** | `QueryAuthorizer` hook: deny (403), forced predicates (e.g. `tenantId`), per-call field hiding |
| **Metadata** | `/metadata` endpoint - clients build UIs without hard-coding the field list |
| **Pagination** | Always paginated, capped by `max-page-size`, stable ordering (`id` appended) |
| **Errors** | Readable validation error list with the path in the filter tree (`400`), never a `500` for bad input |
| **Auto-configuration** | Spring Boot starter; every bean is `@ConditionalOnMissingBean` - replaceable |

## What it looks like

```java
@Entity
@Queryable(name = "product")
public class Product {
    @Id @QueryableField private Long id;
    @QueryableField private String name;
    @QueryableField private BigDecimal price;
    @ManyToOne @QueryableField(nested = {"id", "name"}) private Category category;
    private String internalNote;   // no annotation -> invisible to the API
}
```

```http
POST /api/bq/product/query
Content-Type: application/json

{
  "select":  ["id", "name", "price", "category.name"],
  "filters": { "logic": "and", "children": [
    { "field": "category.name", "op": "EQ", "value": "Electronics" },
    { "field": "price",         "op": "GT", "value": 500 }
  ]},
  "sort": [{ "field": "price", "direction": "ASC" }],
  "page": { "number": 0, "size": 20 }
}
```

```json
{
  "rows": [ { "id": 5, "name": "Pixel Camera X", "price": 699.00, "category.name": "Electronics" } ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

## Requirements

- Java 17+ (to build: a full JDK 17-21), Spring Boot **4.1.x**, Spring MVC (web) and JPA/Hibernate,
- artifacts on Maven Central: `io.github.mszajner:beanquery-starter` (recommended) or `beanquery-core`.

## Documentation map

1. [Quick start]({{ '/getting-started/' | relative_url }}) - from zero to a working query in minutes.
2. [Entity mapping]({{ '/entities/' | relative_url }}) - annotations, types, nested fields.
3. [API reference]({{ '/api/' | relative_url }}) - the `/metadata` and `/query` contract, error codes.
4. [Filters and operators]({{ '/filters/' | relative_url }}) - syntax, value conversion, limits.
5. [Client integration]({{ '/client/' | relative_url }}) - building a UI from the metadata.
6. [Security and multi-tenancy]({{ '/security/' | relative_url }}) - **read before going to production**.
7. [References and module boundaries]({{ '/references/' | relative_url }}) - `JOINED`, `REFERENCE`, SQL view.
8. [Configuration and extension]({{ '/configuration/' | relative_url }}) - properties and replacing beans.
9. [Limitations and roadmap]({{ '/limitations/' | relative_url }}) - what the library does not (yet) do.
10. [Troubleshooting]({{ '/troubleshooting/' | relative_url }}).
