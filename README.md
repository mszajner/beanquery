# beanquery

[![CI](https://github.com/mszajner/beanquery/actions/workflows/ci.yml/badge.svg)](https://github.com/mszajner/beanquery/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.mszajner/beanquery-starter.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.mszajner/beanquery-starter)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Whitelist-first REST query API over JPA entities for Spring Boot.

You annotate the entities and fields you want to expose; beanquery serves a
metadata endpoint and a query endpoint that turn JSON requests into safe JPA
Criteria queries. A field is reachable **only** if it is annotated - request
strings are never passed through to JPA.

- `GET  /api/bq/{entity}/metadata` - the fields a client may use, with types and allowed operators
- `POST /api/bq/{entity}/query` - `{ select, filters, sort, page }` → `{ rows, page }`

---

## Quick start

### 1. Dependency

```xml
<dependency>
    <groupId>io.github.mszajner</groupId>
    <artifactId>beanquery-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

The starter auto-configures itself when a JPA `EntityManagerFactory` and Spring
MVC are on the classpath.

### 2. Annotate entities

```java
@Entity
@Queryable(name = "product")            // URL id; defaults to the decapitalised class name
public class Product {

    @Id
    @QueryableField
    private Long id;

    @QueryableField
    private String name;

    @QueryableField
    private BigDecimal price;

    @QueryableField
    private LocalDate releasedOn;

    @QueryableField
    @Enumerated(EnumType.STRING)
    private Status status;               // enum -> exposed with its values

    @ManyToOne
    @QueryableField(nested = {"id", "name"})   // depth-1 association -> "category.id", "category.name"
    private Category category;

    private String internalNote;         // not annotated -> invisible to the API
}
```

`@QueryableField` attributes: `selectable` / `filterable` / `sortable` (all
`true` by default), `operators` (empty = derive from the field type), and
`nested` (required on `@ManyToOne` / `@OneToOne`, forbidden elsewhere).

### 3. Call the API

**Request** - `POST /api/bq/product/query`

`filters` is an `AND`/`OR` tree - here: *in Elektronika, and either a "pro" model
or over 500*:

```json
{
  "select": ["id", "name", "price", "category.name"],
  "filters": {
    "logic": "and",
    "children": [
      { "field": "category.name", "op": "EQ", "value": "Elektronika" },
      {
        "logic": "or",
        "children": [
          { "field": "name",  "op": "ILIKE", "value": "pro" },
          { "field": "price", "op": "GT",    "value": 500 }
        ]
      }
    ]
  },
  "sort": [{ "field": "price", "direction": "ASC" }],
  "page": { "number": 0, "size": 20 }
}
```

`filters` also accepts the legacy flat array `[ {...}, {...} ]` (combined with
`AND`) - see [Filters: flat list or tree](#filters-flat-list-or-tree).

**Response**

```json
{
  "rows": [
    { "id": 10, "name": "USB-C Dock Pro", "price": 149.00, "category.name": "Elektronika" },
    { "id": 5,  "name": "Pixel Camera X", "price": 699.00, "category.name": "Elektronika" }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 5, "totalPages": 1 }
}
```

Row keys are exactly the `select` entries, in order.

**Metadata** - `GET /api/bq/product/metadata`

```json
{
  "entity": "product",
  "fields": [
    { "name": "price", "type": "number", "selectable": true, "filterable": true, "sortable": true,
      "operators": ["EQ","NE","GT","GTE","LT","LTE","BETWEEN","IN","IS_NULL","IS_NOT_NULL"] },
    { "name": "status", "type": "enum", "values": ["DRAFT","ACTIVE","DISCONTINUED"],
      "selectable": true, "filterable": true, "sortable": true,
      "operators": ["EQ","NE","IN","NOT_IN","IS_NULL","IS_NOT_NULL"] }
  ],
  "capabilities": { "filterLogic": ["AND", "OR"], "maxFilterDepth": 5, "maxFilterConditions": 50 }
}
```

The `capabilities` block tells a client what the `filters` tree may contain for
this deployment.

Try it end to end with the [`beanquery-demo`](beanquery-demo) module
(`mvn -pl beanquery-demo spring-boot:run`), then run the worked AND/OR examples in
[`beanquery-demo/demo.http`](beanquery-demo/demo.http) (IntelliJ) or
[`beanquery-demo/demo.curl.sh`](beanquery-demo/demo.curl.sh) (curl + jq).

---

## Operators

| Operator | Meaning | `value` shape |
|---|---|---|
| `EQ`, `NE` | equal / not equal | scalar |
| `GT`, `GTE`, `LT`, `LTE` | ordered comparison | scalar |
| `BETWEEN` | inclusive range | 2-element array `[lo, hi]` |
| `IN`, `NOT_IN` | membership | non-empty array |
| `LIKE` | case-sensitive *contains*; `%` and `_` in the value are matched literally | scalar (string) |
| `ILIKE` | case-insensitive *contains* | scalar (string) |
| `IS_NULL`, `IS_NOT_NULL` | null test | omit `value` |

### Default operators per type

If `@QueryableField(operators = {})` is left empty, the set is derived from the
field's Java type:

| Type | Default operators |
|---|---|
| `String` | `EQ`, `NE`, `LIKE`, `ILIKE`, `IN`, `IS_NULL`, `IS_NOT_NULL` |
| `Number` (incl. primitives), `BigDecimal`, `LocalDate`, `LocalDateTime`, `Instant` | `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `BETWEEN`, `IN`, `IS_NULL`, `IS_NOT_NULL` |
| `boolean` / `Boolean` | `EQ`, `NE`, `IS_NULL`, `IS_NOT_NULL` |
| `enum` | `EQ`, `NE`, `IN`, `NOT_IN`, `IS_NULL`, `IS_NOT_NULL` |

Filter values are parsed against the target field type - dates as ISO-8601,
enums by name (case-insensitive), plus `BigDecimal` and `UUID`. A value that
does not parse is reported in the `400` error list, never as a `500`.

### Metadata field types

`metadata` reports a simplified `type`: `string`, `number`, `date`,
`datetime`, `boolean`, or `enum` (with a `values` array).

---

## Filters: flat list or tree

`filters` accepts **two shapes**, so existing clients keep working:

**Flat array** - every clause `AND`-combined (the original format):

```json
"filters": [
  { "field": "status", "op": "EQ", "value": "ACTIVE" },
  { "field": "price",  "op": "GT", "value": 100 }
]
```

**Tree** - an object combining conditions with `AND` / `OR`, nested arbitrarily
(the shape is deduced from the keys - a `field` key is a condition, a `logic`
key is a group; no `type` discriminator):

```json
"filters": {
  "logic": "and",
  "children": [
    { "field": "status", "op": "EQ", "value": "NEW" },
    {
      "logic": "or",
      "children": [
        { "field": "totalAmount",   "op": "GT",    "value": 1000 },
        { "field": "customer.name", "op": "ILIKE", "value": "acme" }
      ]
    }
  ]
}
```

`logic` is case-insensitive (`"and"` / `"AND"`). An association used in more than
one branch is still joined once. The old array is treated as
`{ "logic": "AND", "children": [ ... ] }`; an empty array or a missing `filters`
means "no filter".

Limits (both configurable, exceeding either is a `400`, never a `500`):

- **depth** - how deeply groups may nest (`beanquery.max-filter-depth`, default 5)
- **conditions** - total number of leaf conditions in one request
  (`beanquery.max-filter-conditions`, default 50)

Validation errors carry the path in the tree, e.g.
`filters.children[1].children[0]: field 'x' is not filterable`.

---

## Configuration

All properties are under the `beanquery.*` prefix.

| Property | Default | Description |
|---|---|---|
| `beanquery.enabled` | `true` | Set `false` to switch the API off entirely. |
| `beanquery.base-path` | `/api/bq` | Base path the two endpoints are mounted under. |
| `beanquery.max-page-size` | `200` | Largest `page.size` a request may ask for; larger is a `400`. |
| `beanquery.default-page-size` | `20` | `page.size` used when a request omits `page`. |
| `beanquery.max-filter-depth` | `5` | Maximum nesting depth of AND/OR filter groups. |
| `beanquery.max-filter-conditions` | `50` | Maximum total number of filter leaf conditions per request. |

Every bean the starter registers is `@ConditionalOnMissingBean`, so you can
replace the registry, validator, converter or executor by declaring your own.

---

## Security model

- **Whitelist-first.** Every field named in `select`, `filters` or `sort` must be
  registered via `@QueryableField`. Anything else is a `400` with a readable
  message. Request field names are resolved against metadata and only the
  registered property path is handed to the Criteria API - they are never
  concatenated into JPQL or SQL.
- **Per-capability flags.** `selectable` / `filterable` / `sortable` are enforced
  independently, so a field can be returnable but not filterable, etc.
- **Operator allow-list.** A filter operator must be in the field's
  `allowedOperators`; `LIKE` / `ILIKE` always wrap the value as a literal
  *contains* match with `%` and `_` escaped, so a client cannot inject wildcards.
- **Bounded result size.** `page.size` is capped at `beanquery.max-page-size`
  (default 200) and every query is paginated.
- **Read-only.** Queries run in a read-only transaction; there is no write path.
- **Errors don't leak.** Validation and conversion problems return a structured
  `400`; access denial a bare `403`; anything unexpected a generic `500` with
  details only in the server log.
- **Authorization hook.** Row-level filtering and per-role field visibility are
  the host's job via `QueryAuthorizer` - see below.

---

## Security / multi-tenancy

beanquery knows nothing about Spring Security or your permission model. It gives
you one hook: register a `QueryAuthorizer` bean. For every `/metadata` and
`/query` call it can

- **deny** the call (`403`, body `{ "error": "access_denied" }`),
- **force predicates** onto the request - `AND`-ed *over the whole user filter
  tree*, so a user's top-level `OR` can never widen past them, and applied to the
  `COUNT` query too, and
- **hide fields** - a hidden field behaves exactly like an unregistered one
  (using it is a `400` "unknown field"; it is dropped from `/metadata`).

> ⚠️ **With no `QueryAuthorizer` registered, every authenticated caller can query
> every row of every `@Queryable` entity.** In a multi-tenant app this hook is
> mandatory - a missing `tenantId` predicate is a data leak, so a misconfigured
> authorizer fails the request with a `500` rather than running unfiltered.

Authorizers are ordered by `@Order`; mandatory filters and hidden fields from all
of them are aggregated; any `DENY` wins.

### Example 1 - tenant isolation from the Spring Security context

```java
@Component
public class TenantAuthorizer implements QueryAuthorizer {

    @Override
    public boolean supports(EntityMetadata meta) {
        return meta.field("tenantId").isPresent();   // only entities that carry a tenant column
    }

    @Override
    public QueryAuthorization authorize(QueryAuthorizationContext ctx) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUser user)) {
            return QueryAuthorization.deny();
        }
        return QueryAuthorization.builder()
                .mandatoryFilter("tenantId", FilterOperator.EQ, user.tenantId())  // value already typed
                .hideField("tenantId")                                            // callers never see/use it
                .allow();
    }
}
```

Every `securedOrder` query now runs `... AND tenant_id = ?` in both the row query
and the count; a request body of `{"filters":{"logic":"or","children":[...]}}`
cannot escape it.

### Example 2 - hide a cost field from non-admins

```java
@Component
@Order(20)
public class CostFieldAuthorizer implements QueryAuthorizer {

    @Override
    public boolean supports(EntityMetadata meta) {
        return meta.name().equals("product");
    }

    @Override
    public QueryAuthorization authorize(QueryAuthorizationContext ctx) {
        boolean admin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return admin
                ? QueryAuthorization.allow()
                : QueryAuthorization.builder().hideField("purchasePrice").allow();
    }
}
```

A non-admin selecting/filtering/sorting on `purchasePrice` gets
`400 "unknown field 'purchasePrice'"`, and `GET /api/bq/product/metadata` omits
it entirely - no hint that the field exists.

`MandatoryFilter` values are passed straight through (the host knows the field
types); the field must be a registered `@QueryableField` and the operator must be
in its `allowedOperators`, otherwise it is a host configuration error (`500`,
logged with the entity, field and authorizer class).

---

## Version 1 limitations

- **Join depth 1.** Nested paths may reference a single `@ManyToOne` /
  `@OneToOne` hop (`customer.name`). `customer.address.city` is rejected.
  Nested fields must be declared up-front via `@QueryableField(nested = {...})`.
- **Bounded filter tree.** `AND`/`OR` groups nest up to `max-filter-depth`
  levels with at most `max-filter-conditions` leaves; deeper or wider is a `400`.
- **To-one associations only.** `@OneToMany` / `@ManyToMany` are not exposed.
- **No aggregation.** No `GROUP BY`, `COUNT`, `SUM`, `DISTINCT` or projections
  beyond selecting registered scalar fields.
- **No computed or aliased fields.** A field maps directly to one entity property.

---

## Modules

| Module | Coordinates | Purpose | Published |
|---|---|---|---|
| `beanquery-core` | `io.github.mszajner:beanquery-core` | Metadata registry, JPA Criteria query engine, REST layer | yes |
| `beanquery-starter` | `io.github.mszajner:beanquery-starter` | Spring Boot auto-configuration | yes |
| `beanquery-demo` | `io.github.mszajner:beanquery-demo` | Runnable demo (H2, 55 rows, AND/OR examples) | no |

## Build

```bash
./mvnw clean verify
```

Requires a full **JDK 17–21** (`JAVA_HOME` must point at a JDK, not a JRE — a JRE
cannot compile with `--release 17`). Java 17 is the language target; Spring Boot
4.1.x. Maven comes from the wrapper (`./mvnw`).

## Contributing & releasing

- [`CONTRIBUTING.md`](CONTRIBUTING.md) — dev setup, ground rules
- [`RELEASING.md`](RELEASING.md) — Maven Central setup and the tag-to-publish flow
- [`SECURITY.md`](SECURITY.md) — reporting vulnerabilities
- [`CHANGELOG.md`](CHANGELOG.md)

## License

[Apache License 2.0](LICENSE). `SPDX-License-Identifier: Apache-2.0`.
