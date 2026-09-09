# Cross-module references — `ReferenceResolver`

**Status:** approved for planning · **Date:** 2026-09-09 · **Task:** ZADANIE 12

## Problem

In a Spring Modulith application an entity may not hold a JPA association to an
entity owned by another module. beanquery's depth-1 nested paths
(`customer.name` via a `@ManyToOne` `LEFT JOIN`) therefore cannot cross a module
boundary.

This feature adds **reference fields**: the entity stores only a foreign-key id
column (`customerId`); the values behind `customer.name` / `customer.email` are
supplied at runtime by a bean the owning module registers. The library never
learns where that data comes from (repository, cache, remote API).

Two mechanisms then coexist, chosen by the entity author:

| | `JOINED` (`@QueryableField(nested=…)`) | `REFERENCE` (`@QueryableReference`) |
|---|---|---|
| Mechanism | `@ManyToOne` / `@OneToOne` + `LEFT JOIN` | id column + `ReferenceResolver` bean |
| Module boundary | same module only | crosses a boundary |
| Filtering | in SQL | resolver translates to an id set → `IN` |
| Sorting | in SQL | **not supported** (see §5) |

## Scope

In: the annotation, the `ReferenceResolver` SPI, registry validation, filter
translation, row enrichment, one config property, `QueryAuthorizer` interaction,
a two-module demo with `ApplicationModules.verify()`, README + CHANGELOG.

Out (v1): reference field types other than `string`; sorting on reference
fields; nested reference paths (`customer.company.name`); caching resolver
results across requests.

## Naming note

The original task text used the property prefix `rexoft.dynamic-query.*`. That
predates the GitHub/Maven-Central rebrand. Everything here uses the current
`beanquery.*` prefix and `io.github.mszajner.beanquery.*` packages.

---

## 1. Annotation — `@QueryableReference`

New in `io.github.mszajner.beanquery.core.annotation`. `@Target(FIELD)`,
`@Retention(RUNTIME)`, `@Documented`.

```java
@QueryableReference(
    name = "customer",                    // field-name prefix in the API
    fields = { "name", "email" },          // sub-fields the resolver serves
    operators = { EQ, NE, IN, ILIKE }      // advertised operators; {} = default set
)
@QueryableField                            // the id field stays a normal column field
private Long customerId;
```

### Attributes

| Attribute | Type | Default | Meaning |
|---|---|---|---|
| `name` | `String` | *(required, non-blank)* | prefix for the exposed field names (`customer.name`) and the key the resolver is matched on (`ReferenceResolver.referenceName()`) |
| `fields` | `String[]` | *(required, non-empty)* | sub-field names exposed; no dots (depth 1) |
| `operators` | `FilterOperator[]` | `{}` | advertised operators for every sub-field; `{}` ⇒ `EQ, NE, IN, NOT_IN, LIKE, ILIKE` |

### Placement rules (validated at startup, fail-fast)

- Must sit on a field that **also** carries `@QueryableField`.
- That field must be a single scalar (the FK id). Not on an association, not on a
  collection.
- `name` must not collide with another field name or another reference `name` on
  the same entity.
- `fields` entries must be non-blank and contain no `.`.
- A `ReferenceResolver` bean with `referenceName().equals(name)` must exist.
  Missing ⇒ context start fails with a message naming the entity and the
  reference. Silent degradation would surface later as a confusing 400.

### Operator semantics

- The sub-fields (`customer.name`, …) advertise `operators` (or the default set)
  in `/metadata`.
- `IS_NULL` / `IS_NOT_NULL` are **always** permitted on the sub-fields
  regardless of `operators`, and are rewritten against the local id column
  (`customerId IS [NOT] NULL`) — no resolver call. Rationale: the only null
  question a reference field can answer is "is there a referenced row", which is
  exactly the FK column.
- Everything else goes to `resolveFilter`; `Optional.empty()` ⇒ 400.
- Reference sub-fields are `sortable = false` unconditionally; a `sort` on one is
  a 400 (see §5).

---

## 2. Metadata model

### `FieldKind`

New enum in `io.github.mszajner.beanquery.core.metadata`:

```java
public enum FieldKind { COLUMN, JOINED, REFERENCE }
```

### `FieldMetadata`

Gains `FieldKind kind` as a record component (public record change, acceptable
pre-1.0). All construction sites updated:

- existing scalar field → `COLUMN`
- existing `@QueryableField(nested=…)` sub-field → `JOINED`
- reference sub-field → `REFERENCE`

For a reference sub-field:

| component | value |
|---|---|
| `name` | `"customer.name"` |
| `path` | `""` (never used for SQL; kept non-null for the record contract) |
| `javaType` | `String.class` (v1) |
| `selectable` | `true` |
| `filterable` | `true` |
| `sortable` | `false` (always) |
| `allowedOperators` | annotation `operators` (or default set) **plus** `IS_NULL`, `IS_NOT_NULL` |
| `kind` | `REFERENCE` |

### `ReferenceMetadata`

New record in `io.github.mszajner.beanquery.core.metadata`:

```java
public record ReferenceMetadata(String name, String idFieldPath, List<String> fields) {}
```

Links `"customer"` → the id column path `"customerId"` → the exposed sub-field
short names. Consumed by the executor to know which id column to add/collect and
which resolver to call.

### `EntityMetadata`

Gains `List<ReferenceMetadata> references` alongside `fields`. `field(name)` and
`fieldsByName()` unchanged (reference sub-fields are ordinary entries in
`fields`). Add `references(name)` lookup and a `referenceForIdPath(path)` helper.

### `/metadata` response

`MetadataResponse.FieldDescriptor` gains `String kind` (`"COLUMN"` / `"JOINED"` /
`"REFERENCE"`) so a client can tell a reference field from a column and know not
to sort it. `type` stays `"string"` for reference fields in v1.

---

## 3. Resolver SPI

New package `io.github.mszajner.beanquery.core.reference`.

```java
public interface ReferenceResolver {

    /** Must equal the {@code name} of the {@code @QueryableReference} it serves. */
    String referenceName();

    /**
     * Batch lookup: for a set of id values, return id → (sub-field → value).
     * A missing id key means "no such referenced row" — callers render every
     * sub-field as null. Never called with an empty id set.
     */
    Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields);

    /**
     * Translate one filter on a reference sub-field into the set of local id
     * values that match. {@code field} is the sub-field short name
     * ({@code "name"}, not {@code "customer.name"}). {@code value} is the
     * JSON-converted value ({@code String}, or {@code List<Object>} of strings
     * for {@code IN}/{@code NOT_IN}). {@code Optional.empty()} means the resolver
     * does not support this field/operator combination (→ HTTP 400). An empty
     * set means "supported, nothing matches" (→ always-false predicate).
     */
    Optional<Set<Object>> resolveFilter(String field, FilterOperator op, Object value);
}
```

### `ReferenceResolvers` (internal holder)

`Map<String, ReferenceResolver>` built from the ordered bean list. Duplicate
`referenceName()` ⇒ fail-fast at startup. Passed to `DynamicQueryExecutor` as a
new (3rd) constructor argument. The starter collects `List<ReferenceResolver>`
beans exactly as it does for `QueryAuthorizer` (`ObjectProvider.orderedStream`).

The registry cross-checks at startup: every `@QueryableReference.name` in a
`@Queryable` entity has a matching resolver, and (warn only) every resolver
matches some reference.

---

## 4. Execution pipeline (Approach A)

All reference handling lives in `DynamicQueryExecutor` (+ a package-private
`ReferenceEnricher` helper). Rationale: filter translation must happen before
both the row query and the `COUNT` query are built, and it must cover
host-supplied `MandatoryFilter`s — one owner avoids splitting that logic.

### 4a. Filter translation

A new pass runs on the merged `ResolvedFilterNode` tree (user tree +
mandatory predicates, after `withMandatory(...)`), before `fetchRows` / `count`.

For each `ResolvedFilterNode.Condition` whose `field().kind() == REFERENCE`:

1. `IS_NULL` / `IS_NOT_NULL` → replace with
   `Condition(idField, sameOp, null)`, where `idField` is resolved from
   `ReferenceMetadata.idFieldPath`. No resolver call.
2. otherwise → `resolver.resolveFilter(subFieldShortName, op, value)`:
   - `Optional.empty()` → `InvalidQueryException`
     (`"operator " + op + " is not supported for field 'customer.name'"`) → 400.
   - present & size > `beanquery.max-reference-filter-ids` →
     `InvalidQueryException` suggesting a narrower filter → 400.
   - present & non-empty → replace with `Condition(idField, IN, idList)`.
   - present & empty → replace with `ResolvedFilterNode.AlwaysFalse`.

`value` handed to `resolveFilter` is the JSON value converted as `String`
(reference fields are `type: string` in v1): a scalar for most operators, a
`List<Object>` for `IN` / `NOT_IN`. The resolver interprets it.

### 4b. `ResolvedFilterNode.AlwaysFalse`

New leaf added to the sealed interface:

```java
record AlwaysFalse() implements ResolvedFilterNode {}
```

`toPredicate` renders it as `cb.equal(cb.literal(1), cb.literal(0))` — a literal
`1 = 0`, deliberately **not** `cb.disjunction()` (some providers drop an empty
disjunction). Inside an `OR` group it contributes nothing; inside an `AND` group
it forces the branch false. The existing group-flattening in `toPredicate`
(`parts.length == 0 → null`, `== 1 → parts[0]`) is unaffected because
`AlwaysFalse` always yields a non-null predicate.

The same translated tree is passed to `fetchRows` and `count`, so
`totalElements` is correct — including `0` for the empty-id-set case.

### 4c. Row enrichment (`ReferenceEnricher`)

After `fetchRows` returns the page (before building `QueryResult`):

1. If `select` names any `REFERENCE` field, for each referenced `name`:
   - if the reference's id column is not already in `select`, add it to the
     Criteria `selection` list and record its tuple index; mark it "internal"
     so it is dropped from the emitted row maps.
2. For each reference present in `select`:
   - gather the distinct non-null id values from the page;
   - if none, every row's sub-fields are `null`, no call;
   - else **one** `resolve(ids, fields)` call;
   - for each row: look up its id in the returned map, put
     `row.put("customer.name", valueOrNull)` for each requested sub-field.
     Null id, or id absent from the map → `null` for every sub-field.

Invariant: **at most one `resolve()` call per reference per request**. No
per-row calls, no calls during `count`.

Enrichment runs inside `execute(...)`; `QueryResult.rows` is already complete.
`QueryResponse` and the controller are unchanged.

### 4d. Selection / row-key bookkeeping

`fetchRows` currently maps `selectFields.get(i).name()` → `tuple.get(i)`. With an
implicit id column the Criteria selection list is: the `COLUMN`/`JOINED` select
fields, then any trailing internal id columns. `REFERENCE` select fields are
**never** added to the Criteria selection (their `path` is empty and must not
reach `PathResolver`); they exist only as row-map keys the enricher fills. Row
assembly iterates **all** user-visible `selectFields` in request order, writing
`tuple.get(idx)` for `COLUMN`/`JOINED` and a `null` placeholder for `REFERENCE`
(so the key order in the `LinkedHashMap` matches `select`); the enricher then
overwrites the `REFERENCE` keys in place. Order of columns in the response is
exactly `select`. Likewise `toPredicate`
never sees a `REFERENCE` condition (all replaced in §4a) and `buildOrder` never
sees one (blocked by the `sortable == false` validator check).

---

## 5. Sorting — why reference fields are `sortable = false`

Sorting after enrichment would sort **one already-paginated page**, not the full
result — wrong and silently so. Correct cross-boundary sorting would require
fetching every candidate id, sorting at the resolver, then paginating the id
list — deliberately out of scope for v1. `sort` on a reference field is a 400
via the existing `QueryRequestValidator` sortable check (no new code — the
metadata flag carries it). Documented in `ReferenceResolver` javadoc and README.

---

## 6. Configuration

`BeanQueryProperties` gains:

| Property | Default | Meaning |
|---|---|---|
| `beanquery.max-reference-filter-ids` | `1000` | Largest id set `resolveFilter` may return before the request is rejected (400). Guards against a huge `IN (…)` and against driver bind-parameter limits (e.g. PostgreSQL 65535). |

Bound and validated (`>= 1`) like the other limits. `BeanQueryAutoConfiguration`
passes it to the executor (as a plain `int` constructor arg, or via a small
`ReferenceProperties`/limits value object — plan step decides). `demo`
`application.yml` shows it explicitly with the default.

---

## 7. `QueryAuthorizer` interaction

- **Hidden fields**: `AppliedAuthorization.visibleMetadata(meta)` already filters
  `meta.fields()`. Extend it to also carry `references` through, dropping any
  sub-field name that is hidden and dropping a `ReferenceMetadata` whose
  sub-fields are all hidden. A hidden `customer.name` then behaves exactly like
  an unregistered field (400 "unknown field"; absent from `/metadata`).
- **`MandatoryFilter` on a reference sub-field**: `QueryAuthorizationService`
  currently validates `field.allows(op)` and emits a resolved
  `ResolvedFilterNode.Condition`. For `kind == REFERENCE` it instead emits an
  **unresolved** `Condition(referenceSubField, op, value)` (value passed straight
  through, as today for column mandatory filters) and lets §4a translate it —
  same `resolveFilter` path, same `AlwaysFalse`-on-empty. Consequence: a
  mandatory `customer.tenantId EQ "t1"` that resolves to no ids yields `1 = 0`
  and denies access; it can never widen the result. Operator/field validity for
  the reference case is reported by §4a as a 400 (not the 500 used for genuine
  host misconfiguration on column mandatory filters), which is acceptable —
  resolver support is a runtime property, not a static one.

---

## 8. Demo restructure

`beanquery-demo` becomes a Spring Modulith application under
`io.github.mszajner.beanquery.demo`:

- **`product`** — `Product` + `Category`, unchanged. The `JOINED` / `@ManyToOne`
  example.
- **`order`** — `Order` entity: `Long id`, `String status`, `BigDecimal total`,
  plain `Long customerId` column (**no** `@ManyToOne`),
  `@QueryableReference(name = "customer", fields = {"name", "tier"})` on
  `customerId` (also `@QueryableField`).
- **`customer`** — `Customer` entity + `customer` table, and
  `CustomerReferenceResolver` (`@Component`, reads the `customer` table with
  `JdbcTemplate`). `resolveFilter` supports `EQ` / `ILIKE` on `name` and `EQ` on
  `tier`; returns `Optional.empty()` for anything else.
- `data.sql` gains ~6 `customer` rows and ~15 `order` rows, some with
  `customer_id = NULL`, at least one `order` pointing at a `customer_id` with no
  matching row.
- `DemoApplication` stays at the base package; `@Modulithic` or package-level
  `package-info.java` per module.
- `demo.http` and `demo.curl.sh`: new `### Referencje między modułami` section —
  select `customer.name`; `customer.name ILIKE`; a filter that resolves to an
  empty id set (`totalElements: 0`); a reference filter inside an `OR`; the
  `sort` on `customer.name` → 400 case.

---

## 9. README + CHANGELOG

New `## Spring Modulith / module boundaries` section in `README.md`:

- when to use `JOINED` (association, one module) vs `REFERENCE` (across a
  boundary);
- the **`VIEW` alternative** in full: a `CREATE VIEW order_with_customer AS
  SELECT o.*, c.name AS customer_name, c.tier AS customer_tier FROM …` example,
  an `@Entity @Immutable @Subselect(...)` (or view-mapped) class with flat
  columns exposed as ordinary `@QueryableField`s, and the trade-off: full
  SQL-side filtering **and** sorting, paid for with coupling in the database
  schema and a migration to own;
- comparison table **JOINED vs REFERENCE vs VIEW** across: filtering, sorting,
  module boundaries, readiness to extract the module into its own service;
- `beanquery.max-reference-filter-ids` and why it exists.

`CHANGELOG.md` `## [Unreleased]` gains an `### Added` bullet for reference fields.

---

## 10. Tests

### Unit — `beanquery-core`

- **Registry**: `@QueryableReference` with no matching resolver → `initialize`
  throws, message names entity + reference; reference sub-field metadata is
  `kind == REFERENCE`, `sortable == false`, operators = annotation set +
  `IS_NULL/IS_NOT_NULL`; duplicate `referenceName()` across resolvers →
  fail-fast; `@QueryableReference` on a field without `@QueryableField` → throws;
  `name` colliding with a field → throws.
- **Filter translation**: reference `Condition` → `idColumn IN (ids)` (verify the
  resulting `ResolvedFilterNode`); empty id set → `AlwaysFalse`; id set over the
  configured cap → `InvalidQueryException`; `resolveFilter` empty → 
  `InvalidQueryException`; `IS_NULL` on a reference field → id-column `IS NULL`,
  resolver spy asserts **0** `resolveFilter` calls.
- **`AlwaysFalse` predicate**: inside `OR` — sibling still matches; inside `AND` —
  branch is false.
- **Enricher**: page with duplicate + null ids → exactly **one** `resolve()`
  call; correct splice by id; `null` sub-fields for null id and for id missing
  from the resolver's map; id column not requested by the user is absent from
  the row map; id column requested by the user is present.

### Integration — H2 (`beanquery-core` or `beanquery-starter`, `*IT`)

Two simulated modules in the test fixture: an `Order` entity with a `customerId`
column and **no** JPA association; a `customers` table populated by SQL; a spy
`CustomerResolver` backed by `JdbcTemplate` with a call counter.

- select `customer.name` → values match the `customers` table; `customerId =
  NULL` row → `null`;
- exactly **one** `resolve()` call per request (spy counter);
- `customer.name ILIKE "acme"` → only matching orders; `totalElements` correct;
- a filter that resolves to an empty id set → `0` rows, `totalElements == 0`
  (not "all rows");
- `sort` on `customer.name` → 400;
- reference filter inside a tree: `(customer.name EQ "Acme" OR status EQ "NEW")`
  → correct rows, and the translated `IN` appears only in that branch (a row
  with `status = NEW` and a non-matching customer is still returned);
- a `QueryAuthorizer` `MandatoryFilter` on `customer.*` that resolves to an empty
  id set → no rows (safe default), never full access.

### Demo — architecture

`spring-modulith-starter-test` + `ApplicationModules.verify()`: the `order`
module has no dependency on `customer` module classes. Existing demo tests
(`DemoApplicationIT`, `DemoOrFilterTest`) stay green.

---

## 11. Spring Modulith dependency

Not managed by `spring-boot-dependencies` 4.1.1. Pin `spring-modulith-bom` in the
**parent** `dependencyManagement` at the newest build that resolves cleanly
against Spring Boot 4.1.1 (starting point: `2.2.0-M1`). Used **only** by
`beanquery-demo`:

- `spring-modulith-starter-test` (test scope) for `ApplicationModules.verify()`;
- `spring-modulith-starter-core` only if `@ApplicationModule` metadata is needed
  beyond package conventions.

**Contingency**: if no Modulith build works with Boot 4.1.1 during
implementation, fall back to a packages-only module split plus an ArchUnit rule
(`..demo.order..` must not depend on `..demo.customer..`). This will be raised
with the maintainer, not switched silently. `./mvnw clean verify` must be fully
green — including the boundary test — either way.

---

## 12. Public API surface added

- `io.github.mszajner.beanquery.core.annotation.QueryableReference`
- `io.github.mszajner.beanquery.core.metadata.FieldKind`
- `io.github.mszajner.beanquery.core.metadata.ReferenceMetadata`
- `io.github.mszajner.beanquery.core.reference.ReferenceResolver`
- `FieldMetadata` — new `kind` component (breaking)
- `EntityMetadata` — new `references` component (breaking)
- `ResolvedFilterNode.AlwaysFalse` — new permitted type (internal-ish; the
  interface is on the public boundary per `CONTRIBUTING.md`)
- `DynamicQueryExecutor` — new constructor arg (breaking; `@ConditionalOnMissingBean`)
- `MetadataResponse.FieldDescriptor` — new `kind` field (additive)

All acceptable under the pre-1.0 `0.x` contract. `CONTRIBUTING.md`'s "flat filter
array must keep working" and "`/metadata` + `/query` request/response shapes are
a contract" both hold: requests are unchanged; responses gain a field.
