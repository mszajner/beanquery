# Cross-module references (ReferenceResolver) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a `@Queryable` entity expose fields of an entity in another Spring Modulith module through a runtime `ReferenceResolver` bean instead of a JPA association.

**Architecture:** A new `@QueryableReference` annotation on an FK id field registers `REFERENCE` sub-fields (`customer.name`) in the metadata. `DynamicQueryExecutor` gains two stages: it translates filters on reference fields into a local `idColumn IN (…)` predicate via `ReferenceResolver.resolveFilter` (before the row **and** count queries), and after fetching a page it enriches the rows with one batched `ReferenceResolver.resolve` call. Reference fields are never sortable.

**Tech Stack:** Java 17, Spring Boot 4.1.1, JPA Criteria API (Hibernate), JUnit 5, AssertJ, Mockito, H2 (tests), Spring Modulith 2.2.0-M1 (demo test scope only), Maven (`./mvnw`).

**Spec:** `docs/superpowers/specs/2026-09-09-reference-resolver-design.md` — read it alongside this plan.

## Global Constraints

- Java language level **17**; build must pass on JDK 17 and 21 (`./mvnw clean verify`).
- Every source file needs the Apache-2.0 header — run `./mvnw license:format` before committing; `./mvnw verify` fails without it.
- Package root: `io.github.mszajner.beanquery`. Config properties prefix: `beanquery.*`.
- Public API surface per `CONTRIBUTING.md`: `core.{annotation,metadata,query,security}` + the new `core.reference` package. Keep it minimal and Javadoc'd. The `web` package is internal.
- Backwards compatibility: the `/metadata` and `/query` request/response shapes are a contract. Requests are unchanged. Responses may gain fields only.
- Every code change ships with tests. Unit tests sit next to the class; H2 integration tests are named `*IT` and run under Failsafe.
- New config property value: `beanquery.max-reference-filter-ids` default **`1000`**, validated `>= 1`.
- Default reference operator set (annotation `operators` empty): **`EQ, NE, IN, NOT_IN, LIKE, ILIKE`**; `IS_NULL` / `IS_NOT_NULL` are always added and always rewritten to the local id column.
- Spring Modulith BOM version: **`2.2.0-M1`** (resolves against Boot 4.1.1, pulls Spring Framework 7.0.9). Demo module only.
- Add a CHANGELOG bullet under `## [Unreleased]` for the feature (done in Task 15).
- Do not push to `main`; work on a branch/worktree. `main` is protected (PR + green CI required).

---

## File Structure

**`beanquery-core` — new files**

| File | Responsibility |
|---|---|
| `core/annotation/QueryableReference.java` | the annotation |
| `core/metadata/FieldKind.java` | `enum { COLUMN, JOINED, REFERENCE }` |
| `core/metadata/ReferenceMetadata.java` | `record(name, idFieldPath, fields)` linking a reference name to its FK column and sub-fields |
| `core/reference/ReferenceResolver.java` | the host-implemented SPI |
| `core/reference/ReferenceResolvers.java` | internal holder: name → resolver, fail-fast on duplicates |
| `core/reference/package-info.java` | package Javadoc (public API) |
| `core/query/ReferenceFilterTranslator.java` | rewrites `REFERENCE` filter conditions to `idColumn IN (…)` / always-false |
| `core/query/ReferenceEnricher.java` | one batched `resolve()` per reference per page, splices values into rows |

**`beanquery-core` — modified files**

| File | Change |
|---|---|
| `core/metadata/FieldMetadata.java` | add `FieldKind kind` component |
| `core/metadata/EntityMetadata.java` | add `List<ReferenceMetadata> references` + lookups |
| `core/metadata/DefaultOperators.java` | add `referenceDefault()` |
| `core/metadata/QueryableEntityRegistry.java` | scan `@QueryableReference`, build `REFERENCE` fields + `ReferenceMetadata`, validate, cross-check resolver beans |
| `core/query/ResolvedFilterNode.java` | add `AlwaysFalse` permitted record |
| `core/query/DynamicQueryExecutor.java` | new constructor args; run translator + enricher; render `AlwaysFalse`; implicit id column in selection; skip `REFERENCE` in select/sort paths |
| `core/web/MetadataResponse.java` | `FieldDescriptor` gains `String kind` |
| `core/web/MetadataMapper.java` | populate `kind` |
| `core/security/AppliedAuthorization.java` | carry `references` through `visibleMetadata` |
| `core/security/QueryAuthorizationService.java` | emit an unresolved `Condition` for a `REFERENCE` `MandatoryFilter` |

**`beanquery-starter` — modified files**

| File | Change |
|---|---|
| `starter/BeanQueryProperties.java` | `maxReferenceFilterIds` |
| `starter/BeanQueryAutoConfiguration.java` | `ReferenceResolvers` bean; pass it to registry + executor |

**`beanquery-demo` — restructure**

```
io.github.mszajner.beanquery.demo               DemoApplication
io.github.mszajner.beanquery.demo.product       Product, Category           (JOINED example, moved as-is)
io.github.mszajner.beanquery.demo.order         CustomerOrder               (REFERENCE example, new)
io.github.mszajner.beanquery.demo.customer      Customer, CustomerReferenceResolver   (new)
```

**Parent** `pom.xml` — `spring-modulith-bom` in `dependencyManagement`.

**Docs** — `README.md` (new section), `CHANGELOG.md` (bullet).

---

## Task 1: `FieldKind` + `FieldMetadata.kind`

**Files:**
- Create: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/metadata/FieldKind.java`
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/metadata/FieldMetadata.java`
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/metadata/QueryableEntityRegistry.java` (`scalarField`, nested loop)
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/security/AppliedAuthorization.java` (already rebuilds `FieldMetadata`? no — it filters; check)
- Test: `beanquery-core/src/test/java/io/github/mszajner/beanquery/core/metadata/QueryableEntityRegistryTest.java`

**Interfaces:**
- Produces: `enum FieldKind { COLUMN, JOINED, REFERENCE }`; `FieldMetadata` canonical constructor becomes `FieldMetadata(String name, String path, Class<?> javaType, boolean selectable, boolean filterable, boolean sortable, Set<FilterOperator> allowedOperators, FieldKind kind)`; accessor `kind()`.

- [ ] **Step 1: Write the failing test**

Add to `QueryableEntityRegistryTest`:

```java
@Test
void scalarFieldsAreKindColumn() {
    EntityMetadata customer = registryOf(CustomerModel.class).getRequired("customer");
    assertThat(customer.field("name").orElseThrow().kind()).isEqualTo(FieldKind.COLUMN);
}

@Test
void nestedAssociationFieldsAreKindJoined() {
    EntityMetadata order = registryOf(PurchaseOrderModel.class).getRequired("purchaseOrderModel");
    assertThat(order.field("customer.name").orElseThrow().kind()).isEqualTo(FieldKind.JOINED);
}
```

- [ ] **Step 2: Run — expect compile failure** (`kind()` / `FieldKind` unknown)

Run: `./mvnw -q -pl beanquery-core test -Dtest=QueryableEntityRegistryTest`
Expected: compilation failure.

- [ ] **Step 3: Create `FieldKind`**

```java
package io.github.mszajner.beanquery.core.metadata;

/** How a {@link FieldMetadata} is backed. */
public enum FieldKind {

    /** A plain column on the entity root. */
    COLUMN,

    /** A depth-1 {@code @ManyToOne}/{@code @OneToOne} sub-field reached via {@code LEFT JOIN}. */
    JOINED,

    /** A cross-module reference sub-field served by a {@code ReferenceResolver}. */
    REFERENCE
}
```

- [ ] **Step 4: Add `kind` to `FieldMetadata`**

Add `FieldKind kind` as the last record component. In the compact constructor add:

```java
kind = Objects.requireNonNull(kind, "kind");
```

Update the class Javadoc `@param kind` line.

- [ ] **Step 5: Update `QueryableEntityRegistry` construction sites**

In `scalarField(...)` pass `FieldKind.COLUMN` as the last argument. In the nested-fields loop in `buildFields(...)` pass `FieldKind.JOINED` as the last argument to `new FieldMetadata(...)`.

- [ ] **Step 6: Fix remaining compile errors**

Search and update every other `new FieldMetadata(` call:

Run: `grep -rn "new FieldMetadata(" beanquery-core/src`

Expected sites: test helpers in `QueryRequestValidatorTest`, `BeanQueryControllerTest`, `DynamicQueryExecutorIT`, `DefaultOperatorsTest`, `QueryAuthorizationServiceTest`, `MetadataMapper`-adjacent tests. For each existing helper that builds a column field, append `, FieldKind.COLUMN`. Update the shared `field(...)` helpers in those test classes once.

- [ ] **Step 7: Run the full core module**

Run: `./mvnw -q -pl beanquery-core test`
Expected: PASS (new assertions included).

- [ ] **Step 8: License headers + commit**

```bash
./mvnw -q license:format
git add beanquery-core
git commit -m "feat(core): add FieldKind to FieldMetadata"
```

---

## Task 2: `ReferenceMetadata` + `EntityMetadata.references`

**Files:**
- Create: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/metadata/ReferenceMetadata.java`
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/metadata/EntityMetadata.java`
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/metadata/QueryableEntityRegistry.java` (`buildEntityMetadata` return)
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/security/AppliedAuthorization.java` (`visibleMetadata`)
- Test: `beanquery-core/src/test/java/io/github/mszajner/beanquery/core/metadata/EntityMetadataTest.java` (create)

**Interfaces:**
- Produces:
  - `record ReferenceMetadata(String name, String idFieldPath, List<String> fields)` with a compact constructor copying `fields` and null-checking.
  - `EntityMetadata(String name, Class<?> entityClass, List<FieldMetadata> fields, List<ReferenceMetadata> references)` — new last component.
  - `List<ReferenceMetadata> EntityMetadata.references()`
  - `Optional<ReferenceMetadata> EntityMetadata.reference(String name)` — by reference name
  - `Optional<ReferenceMetadata> EntityMetadata.referenceForField(String fieldName)` — splits `fieldName` on the first `.` and looks the prefix up

- [ ] **Step 1: Write the failing test** — create `EntityMetadataTest`:

```java
package io.github.mszajner.beanquery.core.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EntityMetadataTest {

    private static final EntityMetadata META = new EntityMetadata(
            "order", Object.class, List.of(),
            List.of(new ReferenceMetadata("customer", "customerId", List.of("name", "email"))));

    @Test
    void referenceLookupByName() {
        assertThat(META.reference("customer")).get()
                .extracting(ReferenceMetadata::idFieldPath).isEqualTo("customerId");
    }

    @Test
    void referenceLookupByDottedFieldName() {
        assertThat(META.referenceForField("customer.name")).get()
                .extracting(ReferenceMetadata::name).isEqualTo("customer");
        assertThat(META.referenceForField("status")).isEmpty();
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

Run: `./mvnw -q -pl beanquery-core test -Dtest=EntityMetadataTest`
Expected: compile failure.

- [ ] **Step 3: Create `ReferenceMetadata`**

```java
package io.github.mszajner.beanquery.core.metadata;

import java.util.List;
import java.util.Objects;

/**
 * Links a {@code @QueryableReference} to the local foreign-key column and the
 * sub-fields a {@code ReferenceResolver} serves.
 *
 * @param name        the reference name / field-name prefix ({@code "customer"})
 * @param idFieldPath the registered {@link FieldMetadata#path()} of the FK id column
 *                    ({@code "customerId"})
 * @param fields      the exposed sub-field short names ({@code ["name", "email"]})
 */
public record ReferenceMetadata(String name, String idFieldPath, List<String> fields) {

    public ReferenceMetadata {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(idFieldPath, "idFieldPath");
        fields = List.copyOf(fields);
    }
}
```

- [ ] **Step 4: Add `references` to `EntityMetadata`**

Add the component, copy in the compact constructor (`references = List.copyOf(references);`), and add:

```java
public Optional<ReferenceMetadata> reference(String referenceName) {
    return references.stream().filter(r -> r.name().equals(referenceName)).findFirst();
}

public Optional<ReferenceMetadata> referenceForField(String fieldName) {
    int dot = fieldName.indexOf('.');
    return dot < 0 ? Optional.empty() : reference(fieldName.substring(0, dot));
}
```

- [ ] **Step 5: Update `QueryableEntityRegistry.buildEntityMetadata`**

Change the return to `new EntityMetadata(name, entityClass, List.copyOf(fields.values()), List.of())` for now (references are wired in Task 5).

- [ ] **Step 6: Update `AppliedAuthorization.visibleMetadata`**

```java
public EntityMetadata visibleMetadata(EntityMetadata meta) {
    if (hiddenFields.isEmpty()) {
        return meta;
    }
    List<FieldMetadata> visible = meta.fields().stream()
            .filter(field -> !hiddenFields.contains(field.name()))
            .toList();
    List<ReferenceMetadata> references = meta.references().stream()
            .map(ref -> new ReferenceMetadata(ref.name(), ref.idFieldPath(),
                    ref.fields().stream()
                            .filter(sub -> !hiddenFields.contains(ref.name() + "." + sub))
                            .toList()))
            .filter(ref -> !ref.fields().isEmpty())
            .toList();
    return new EntityMetadata(meta.name(), meta.entityClass(), visible, references);
}
```

Add the `ReferenceMetadata` import.

- [ ] **Step 7: Fix remaining `new EntityMetadata(` sites**

Run: `grep -rn "new EntityMetadata(" beanquery-core/src`
Append `, List.of()` to each 3-arg call (test builders).

- [ ] **Step 8: Run + license + commit**

```bash
./mvnw -q -pl beanquery-core test
./mvnw -q license:format
git add beanquery-core
git commit -m "feat(core): add ReferenceMetadata to EntityMetadata"
```

---

## Task 3: `@QueryableReference` annotation + `ReferenceResolver` SPI

**Files:**
- Create: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/annotation/QueryableReference.java`
- Create: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/reference/ReferenceResolver.java`
- Create: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/reference/ReferenceResolvers.java`
- Create: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/reference/package-info.java`
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/metadata/DefaultOperators.java`
- Test: `beanquery-core/src/test/java/io/github/mszajner/beanquery/core/reference/ReferenceResolversTest.java` (create)

**Interfaces:**
- Produces:
  - `@interface QueryableReference { String name(); String[] fields(); FilterOperator[] operators() default {}; }` (`@Target(FIELD)`, `@Retention(RUNTIME)`, `@Documented`)
  - `interface ReferenceResolver { String referenceName(); Map<Object,Map<String,Object>> resolve(Set<Object> ids, Set<String> fields); Optional<Set<Object>> resolveFilter(String field, FilterOperator op, Object value); }`
  - `final class ReferenceResolvers` — `ReferenceResolvers(List<ReferenceResolver>)`, `static ReferenceResolvers EMPTY`, `Optional<ReferenceResolver> find(String name)`, `ReferenceResolver require(String name)`, `Set<String> names()`. Duplicate `referenceName()` → `QueryableMetadataException`.
  - `DefaultOperators.referenceDefault()` → `Set<FilterOperator>` = `{EQ, NE, IN, NOT_IN, LIKE, ILIKE}`

- [ ] **Step 1: Write the failing test** — `ReferenceResolversTest`:

```java
package io.github.mszajner.beanquery.core.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.metadata.QueryableMetadataException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceResolversTest {

    static class Fake implements ReferenceResolver {
        private final String name;
        Fake(String name) { this.name = name; }
        public String referenceName() { return name; }
        public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) { return Map.of(); }
        public Optional<Set<Object>> resolveFilter(String f, FilterOperator o, Object v) { return Optional.empty(); }
    }

    @Test
    void indexesByReferenceName() {
        ReferenceResolvers resolvers = new ReferenceResolvers(List.of(new Fake("customer")));
        assertThat(resolvers.names()).containsExactly("customer");
        assertThat(resolvers.find("customer")).isPresent();
        assertThat(resolvers.find("nope")).isEmpty();
    }

    @Test
    void rejectsDuplicateReferenceNames() {
        assertThatThrownBy(() -> new ReferenceResolvers(List.of(new Fake("customer"), new Fake("customer"))))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("customer");
    }

    @Test
    void requireThrowsForUnknownName() {
        assertThatThrownBy(() -> ReferenceResolvers.EMPTY.require("customer"))
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

Run: `./mvnw -q -pl beanquery-core test -Dtest=ReferenceResolversTest`
Expected: compile failure.

- [ ] **Step 3: Create `QueryableReference`**

```java
package io.github.mszajner.beanquery.core.annotation;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exposes fields of an entity owned by another module without a JPA association.
 *
 * <p>Placed on a scalar foreign-key id field that also carries {@link QueryableField}.
 * Each entry in {@link #fields()} becomes a read-only, non-sortable field named
 * {@code "<name>.<field>"}; its values are supplied at query time by the
 * {@code ReferenceResolver} bean whose {@code referenceName()} equals {@link #name()}.
 * The id field itself stays a normal filterable/sortable field under its own name.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueryableReference {

    /** Field-name prefix in the API and the key the resolver is matched on. Must be non-blank. */
    String name();

    /** Sub-field short names to expose ({@code "name"}, {@code "email"}). Non-empty, no dots. */
    String[] fields();

    /**
     * Filter operators advertised for every sub-field. Empty (default) uses
     * {@code EQ, NE, IN, NOT_IN, LIKE, ILIKE}. {@code IS_NULL} / {@code IS_NOT_NULL}
     * are always permitted and always evaluated against the local id column.
     */
    FilterOperator[] operators() default {};
}
```

- [ ] **Step 4: Create `ReferenceResolver`**

```java
package io.github.mszajner.beanquery.core.reference;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Host-implemented lookup for a {@code @QueryableReference}. Register one Spring
 * bean per reference name.
 */
public interface ReferenceResolver {

    /** Must equal the {@code name} of the {@code @QueryableReference} this serves. */
    String referenceName();

    /**
     * Batch lookup: {@code id -> (subField -> value)}. A missing id key means "no
     * such referenced row" and every sub-field renders as {@code null}. Never
     * called with an empty {@code ids} set.
     */
    Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields);

    /**
     * Translate one filter on a reference sub-field to the set of local id values
     * that match. {@code field} is the sub-field short name ({@code "name"}, not
     * {@code "customer.name"}); {@code value} is the JSON-converted value
     * ({@code String}, or {@code List<Object>} of strings for {@code IN}/{@code NOT_IN}).
     *
     * @return {@code Optional.empty()} — this field/operator pair is unsupported (HTTP 400);
     *         an empty set — supported, nothing matches (always-false predicate);
     *         a non-empty set — the matching local id values
     */
    Optional<Set<Object>> resolveFilter(String field, FilterOperator op, Object value);
}
```

- [ ] **Step 5: Create `ReferenceResolvers`**

```java
package io.github.mszajner.beanquery.core.reference;

import io.github.mszajner.beanquery.core.metadata.QueryableMetadataException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Internal registry of {@link ReferenceResolver} beans, keyed by {@code referenceName()}. */
public final class ReferenceResolvers {

    /** No resolvers registered. */
    public static final ReferenceResolvers EMPTY = new ReferenceResolvers(List.of());

    private final Map<String, ReferenceResolver> byName = new LinkedHashMap<>();

    public ReferenceResolvers(List<ReferenceResolver> resolvers) {
        for (ReferenceResolver resolver : resolvers) {
            String name = resolver.referenceName();
            if (byName.putIfAbsent(name, resolver) != null) {
                throw new QueryableMetadataException(
                        "Duplicate ReferenceResolver for reference name '" + name + "': "
                                + byName.get(name).getClass().getName() + " and "
                                + resolver.getClass().getName());
            }
        }
    }

    public Optional<ReferenceResolver> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public ReferenceResolver require(String name) {
        return find(name).orElseThrow(() ->
                new IllegalStateException("No ReferenceResolver registered for reference name '" + name + "'"));
    }

    public Set<String> names() {
        return Set.copyOf(byName.keySet());
    }
}
```

- [ ] **Step 6: Create `reference/package-info.java`**

```java
/**
 * The {@code ReferenceResolver} SPI: expose fields of another module's entity
 * through a runtime lookup instead of a JPA association.
 */
package io.github.mszajner.beanquery.core.reference;
```

- [ ] **Step 7: Add `DefaultOperators.referenceDefault()`**

```java
private static final Set<FilterOperator> REFERENCE_OPERATORS =
        immutable(EQ, NE, IN, NOT_IN, LIKE, ILIKE);

/** Operators advertised for a reference sub-field when the annotation lists none. */
public static Set<FilterOperator> referenceDefault() {
    return REFERENCE_OPERATORS;
}
```

- [ ] **Step 8: Run + license + commit**

```bash
./mvnw -q -pl beanquery-core test -Dtest=ReferenceResolversTest
./mvnw -q license:format
git add beanquery-core
git commit -m "feat(core): add @QueryableReference and ReferenceResolver SPI"
```

---

## Task 4: Registry scans `@QueryableReference`

**Files:**
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/metadata/QueryableEntityRegistry.java`
- Test: `beanquery-core/src/test/java/io/github/mszajner/beanquery/core/metadata/QueryableEntityRegistryReferenceTest.java` (create)

**Interfaces:**
- Consumes: `QueryableReference`, `FieldKind.REFERENCE`, `ReferenceMetadata`, `DefaultOperators.referenceDefault()`.
- Produces: for each `@QueryableReference` field, one `FieldMetadata` per declared sub-field (`name="<ref>.<sub>"`, `path=""`, `javaType=String.class`, `selectable=true`, `filterable=true`, `sortable=false`, operators = annotation set or `referenceDefault()` **plus** `IS_NULL`, `IS_NOT_NULL`, `kind=REFERENCE`) and one `ReferenceMetadata(name, idFieldPath=<field name>, fields=<declared subs>)` on the `EntityMetadata`.
- Validation (throws `QueryableMetadataException`): `@QueryableReference` on a field without `@QueryableField`; `name` blank; `fields` empty or containing a dot; `name` colliding with a field name or another reference name; a sub-field name colliding with an existing field.

- [ ] **Step 1: Write the failing tests** — `QueryableEntityRegistryReferenceTest`:

```java
package io.github.mszajner.beanquery.core.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.github.mszajner.beanquery.core.annotation.Queryable;
import io.github.mszajner.beanquery.core.annotation.QueryableField;
import io.github.mszajner.beanquery.core.annotation.QueryableReference;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryableEntityRegistryReferenceTest {

    private QueryableEntityRegistry registryOf(Class<?>... classes) {
        QueryableEntityRegistry registry =
                new QueryableEntityRegistry(mock(EntityManagerFactory.class), java.util.Set.of("customer"));
        registry.initialize(List.of(classes));
        return registry;
    }

    @Test
    void expandsReferenceIntoSubFieldsAndMetadata() {
        EntityMetadata order = registryOf(OrderModel.class).getRequired("order");

        assertThat(order.fieldsByName()).containsKeys("customerId", "customer.name", "customer.tier");

        FieldMetadata name = order.field("customer.name").orElseThrow();
        assertThat(name.kind()).isEqualTo(FieldKind.REFERENCE);
        assertThat(name.selectable()).isTrue();
        assertThat(name.filterable()).isTrue();
        assertThat(name.sortable()).isFalse();
        assertThat(name.allowedOperators())
                .contains(FilterOperator.ILIKE, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL);

        assertThat(order.reference("customer")).get()
                .satisfies(ref -> {
                    assertThat(ref.idFieldPath()).isEqualTo("customerId");
                    assertThat(ref.fields()).containsExactly("name", "tier");
                });

        assertThat(order.field("customerId").orElseThrow().kind()).isEqualTo(FieldKind.COLUMN);
    }

    @Test
    void rejectsReferenceWithoutQueryableField() {
        assertThatThrownBy(() -> registryOf(RefWithoutQueryableField.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("@QueryableField");
    }

    @Test
    void rejectsReferenceNameCollidingWithAField() {
        assertThatThrownBy(() -> registryOf(RefNameCollision.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("customer");
    }

    @Queryable(name = "order")
    static class OrderModel {
        @QueryableField Long id;
        @QueryableField
        @QueryableReference(name = "customer", fields = {"name", "tier"})
        Long customerId;
    }

    @Queryable(name = "x")
    static class RefWithoutQueryableField {
        @QueryableField Long id;
        @QueryableReference(name = "customer", fields = {"name"})
        Long customerId;
    }

    @Queryable(name = "x")
    static class RefNameCollision {
        @QueryableField Long id;
        @QueryableField String customer;
        @QueryableField
        @QueryableReference(name = "customer", fields = {"name"})
        Long customerId;
    }
}
```

- [ ] **Step 2: Run — expect failure** (2-arg constructor missing, reference not expanded)

Run: `./mvnw -q -pl beanquery-core test -Dtest=QueryableEntityRegistryReferenceTest`
Expected: compile failure on `new QueryableEntityRegistry(emf, Set)`.

- [ ] **Step 3: Add the constructor + field**

```java
private final EntityManagerFactory entityManagerFactory;
private final Set<String> availableReferenceResolverNames;

public QueryableEntityRegistry(EntityManagerFactory entityManagerFactory) {
    this(entityManagerFactory, Set.of());
}

public QueryableEntityRegistry(EntityManagerFactory entityManagerFactory,
        Set<String> availableReferenceResolverNames) {
    this.entityManagerFactory = Objects.requireNonNull(entityManagerFactory, "entityManagerFactory");
    this.availableReferenceResolverNames = Set.copyOf(availableReferenceResolverNames);
}
```

(The cross-check that *uses* `availableReferenceResolverNames` is Task 5 — add the field now, use it next task.)

- [ ] **Step 4: Expand references in `buildEntityMetadata`**

Rework `buildEntityMetadata` to also collect references. Replace its body:

```java
static EntityMetadata buildEntityMetadata(String name, Class<?> entityClass) {
    Map<String, FieldMetadata> fields = new LinkedHashMap<>();
    List<ReferenceMetadata> references = new ArrayList<>();

    ReflectionUtils.doWithFields(entityClass, field -> {
        QueryableField queryableField = field.getAnnotation(QueryableField.class);
        QueryableReference reference = field.getAnnotation(QueryableReference.class);

        if (queryableField != null) {
            for (FieldMetadata fm : buildFields(entityClass, field, queryableField)) {
                putField(fields, fm, entityClass);
            }
        }
        if (reference != null) {
            references.add(buildReference(entityClass, field, reference, fields));
        }
    });

    // reference sub-fields are added after all column/joined fields so collisions are detected either way
    for (ReferenceMetadata ref : references) {
        FieldMetadata idField = fields.get(ref.idFieldPath());
        Set<FilterOperator> ops = referenceOperators(entityClass, ref);
        for (String sub : ref.fields()) {
            putField(fields, new FieldMetadata(
                    ref.name() + "." + sub, "", String.class,
                    true, true, false, ops, FieldKind.REFERENCE), entityClass);
        }
    }

    return new EntityMetadata(name, entityClass, List.copyOf(fields.values()), List.copyOf(references));
}

private static void putField(Map<String, FieldMetadata> fields, FieldMetadata fm, Class<?> entityClass) {
    if (fields.putIfAbsent(fm.name(), fm) != null) {
        throw new QueryableMetadataException(
                "Duplicate queryable field '" + fm.name() + "' on " + entityClass.getName());
    }
}
```

`buildReference` — validate placement and record the reference (sub-fields added in the loop above):

```java
private static ReferenceMetadata buildReference(Class<?> entityClass, Field field,
        QueryableReference reference, Map<String, FieldMetadata> columnFields) {

    if (field.getAnnotation(QueryableField.class) == null) {
        throw new QueryableMetadataException(
                "Field '" + field.getName() + "' on " + entityClass.getName()
                        + " has @QueryableReference but no @QueryableField");
    }
    String refName = reference.name() == null ? "" : reference.name().trim();
    if (refName.isBlank()) {
        throw new QueryableMetadataException(
                "@QueryableReference on '" + field.getName() + "' in " + entityClass.getName()
                        + " has a blank name");
    }
    if (reference.fields().length == 0) {
        throw new QueryableMetadataException(
                "@QueryableReference '" + refName + "' on " + entityClass.getName() + " declares no fields");
    }
    List<String> subs = new ArrayList<>();
    for (String sub : reference.fields()) {
        if (sub == null || sub.isBlank() || sub.contains(".")) {
            throw new QueryableMetadataException(
                    "@QueryableReference '" + refName + "' on " + entityClass.getName()
                            + " has an invalid sub-field '" + sub + "'");
        }
        subs.add(sub.trim());
    }
    if (columnFields.containsKey(refName)) {
        throw new QueryableMetadataException(
                "@QueryableReference name '" + refName + "' on " + entityClass.getName()
                        + " collides with a field of the same name");
    }
    return new ReferenceMetadata(refName, field.getName(), subs);
}

private static Set<FilterOperator> referenceOperators(Class<?> entityClass, ReferenceMetadata ref) {
    // annotation lookup by id field name — re-read the annotation
    FilterOperator[] declared = declaredReferenceOperators(entityClass, ref.idFieldPath());
    EnumSet<FilterOperator> ops = declared.length == 0
            ? EnumSet.copyOf(DefaultOperators.referenceDefault())
            : EnumSet.copyOf(List.of(declared));
    ops.add(FilterOperator.IS_NULL);
    ops.add(FilterOperator.IS_NOT_NULL);
    return Collections.unmodifiableSet(ops);
}

private static FilterOperator[] declaredReferenceOperators(Class<?> entityClass, String fieldName) {
    Field f = ReflectionUtils.findField(entityClass, fieldName);
    QueryableReference r = f == null ? null : f.getAnnotation(QueryableReference.class);
    return r == null ? new FilterOperator[0] : r.operators();
}
```

Add imports: `io.github.mszajner.beanquery.core.annotation.QueryableReference`.

Note on collision detection: because reference sub-fields are added *after* column/joined fields, `putField` catches a sub-field name that duplicates a real column. A reference `name` colliding with a column is caught explicitly in `buildReference`.

- [ ] **Step 5: Run**

Run: `./mvnw -q -pl beanquery-core test -Dtest=QueryableEntityRegistryReferenceTest,QueryableEntityRegistryTest`
Expected: PASS.

- [ ] **Step 6: License + commit**

```bash
./mvnw -q license:format
git add beanquery-core
git commit -m "feat(core): register @QueryableReference sub-fields and metadata"
```

---

## Task 5: Registry fail-fast when a resolver bean is missing

**Files:**
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/metadata/QueryableEntityRegistry.java`
- Test: `beanquery-core/src/test/java/io/github/mszajner/beanquery/core/metadata/QueryableEntityRegistryReferenceTest.java`

**Interfaces:**
- Consumes: `availableReferenceResolverNames` (Task 4 field).
- Produces: after `scan(...)`, every `ReferenceMetadata.name()` across all entities must be in `availableReferenceResolverNames`, else `QueryableMetadataException` naming the entity and reference.

- [ ] **Step 1: Write the failing test**

```java
@Test
void failsFastWhenNoResolverBeanMatches() {
    QueryableEntityRegistry registry =
            new QueryableEntityRegistry(mock(EntityManagerFactory.class), java.util.Set.of()); // no resolvers
    assertThatThrownBy(() -> registry.initialize(List.of(OrderModel.class)))
            .isInstanceOf(QueryableMetadataException.class)
            .hasMessageContaining("order")
            .hasMessageContaining("customer");
}
```

- [ ] **Step 2: Run — expect FAIL** (no check yet, initialize succeeds)

Run: `./mvnw -q -pl beanquery-core test -Dtest=QueryableEntityRegistryReferenceTest#failsFastWhenNoResolverBeanMatches`
Expected: FAIL (no exception thrown).

- [ ] **Step 3: Add the cross-check in `initialize`**

```java
void initialize(Collection<Class<?>> candidateEntityClasses) {
    Map<String, EntityMetadata> scanned = scan(candidateEntityClasses);
    verifyReferenceResolvers(scanned);
    this.entitiesByName = Collections.unmodifiableMap(scanned);
}

private void verifyReferenceResolvers(Map<String, EntityMetadata> scanned) {
    for (EntityMetadata meta : scanned.values()) {
        for (ReferenceMetadata ref : meta.references()) {
            if (!availableReferenceResolverNames.contains(ref.name())) {
                throw new QueryableMetadataException(
                        "Entity '" + meta.name() + "' declares @QueryableReference '" + ref.name()
                                + "' but no ReferenceResolver bean has referenceName() == '" + ref.name() + "'");
            }
        }
    }
}
```

- [ ] **Step 4: Run**

Run: `./mvnw -q -pl beanquery-core test -Dtest=QueryableEntityRegistryReferenceTest`
Expected: PASS.

- [ ] **Step 5: License + commit**

```bash
./mvnw -q license:format
git add beanquery-core
git commit -m "feat(core): fail fast when a @QueryableReference has no resolver bean"
```

---

## Task 6: `ResolvedFilterNode.AlwaysFalse` + executor renders `1 = 0`

**Files:**
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/query/ResolvedFilterNode.java`
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/query/DynamicQueryExecutor.java` (`toPredicate`)
- Test: `beanquery-core/src/test/java/io/github/mszajner/beanquery/core/executor/DynamicQueryExecutorIT.java`

**Interfaces:**
- Produces: `record ResolvedFilterNode.AlwaysFalse() implements ResolvedFilterNode` added to `permits`. `toPredicate` returns `cb.equal(cb.literal(1), cb.literal(0))` for it.

- [ ] **Step 1: Write the failing test** — add a `@Nested` block or a method to `DynamicQueryExecutorIT` (it already has an `EntityMetadata` + executor fixture for `Customer`/`Order`). Use the existing style:

```java
@Test
void alwaysFalseNodeReturnsNoRows() {
    QueryResult result = executor.execute(customerMeta(), new QueryRequest(
            List.of("id"), null, List.of(), new Page(0, 10)));
    long allRows = result.page().totalElements();
    assertThat(allRows).isGreaterThan(0);

    // AND(true-ish, AlwaysFalse) -> nothing
    ResolvedFilterNode tree = new ResolvedFilterNode.Group(LogicalOperator.AND,
            List.of(new ResolvedFilterNode.AlwaysFalse()));
    QueryResult none = executor.executeResolved(customerMeta(), tree, new Page(0, 10)); // see note
    assertThat(none.page().totalElements()).isZero();
}
```

**Note:** if the executor has no package-private "run an already-resolved tree" entry point, add one for testing:
`QueryResult executeResolved(EntityMetadata meta, ResolvedFilterNode resolved, Page page)` that skips `resolveFilters` — or simpler, assert `AlwaysFalse` via a translator test in Task 7 and here only test that `toPredicate` compiles + a full-stack `securedOrder` empty-reference-filter case in Task 12's IT. **Pick the translator-level test (Task 7) as the real coverage; in this task, just add the node + predicate and a one-line compile/scan test.**

Minimal test for this task — add to `DynamicQueryExecutorIT`:

```java
@Test
void alwaysFalsePredicateProducesEmptyResult() {
    ResolvedFilterNode tree = new ResolvedFilterNode.Group(LogicalOperator.OR,
            List.of(new ResolvedFilterNode.AlwaysFalse()));
    QueryResult result = executor.executeResolved(customerMeta(), tree, new Page(0, 10));
    assertThat(result.rows()).isEmpty();
    assertThat(result.page().totalElements()).isZero();
}
```

- [ ] **Step 2: Run — expect compile failure** (`AlwaysFalse`, `executeResolved` unknown)

Run: `./mvnw -q -pl beanquery-core test -Dtest=DynamicQueryExecutorIT#alwaysFalsePredicateProducesEmptyResult`
Expected: compile failure.

- [ ] **Step 3: Add `AlwaysFalse` to `ResolvedFilterNode`**

```java
public sealed interface ResolvedFilterNode
        permits ResolvedFilterNode.Condition, ResolvedFilterNode.Group, ResolvedFilterNode.AlwaysFalse {

    // ... existing Condition, Group ...

    /** A predicate that never matches — used when a reference filter resolves to no ids. */
    record AlwaysFalse() implements ResolvedFilterNode {
    }
}
```

- [ ] **Step 4: Handle it in `DynamicQueryExecutor.toPredicate`**

At the top of `toPredicate`, after the `node == null` check:

```java
if (node instanceof ResolvedFilterNode.AlwaysFalse) {
    return cb.equal(cb.literal(1), cb.literal(0));
}
```

Add a package-private test entry point:

```java
QueryResult executeResolved(EntityMetadata meta, ResolvedFilterNode resolved, Page page) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    List<FieldMetadata> selectFields = resolveSelect(meta, List.of(firstSelectable(meta)));
    List<Map<String, Object>> rows = fetchRows(cb, meta, selectFields, resolved, List.of(), page);
    long total = count(cb, meta, resolved);
    int totalPages = page.size() == 0 ? 0 : (int) ((total + page.size() - 1) / page.size());
    return new QueryResult(rows, new PageInfo(page.number(), page.size(), total, totalPages));
}

private static String firstSelectable(EntityMetadata meta) {
    return meta.fields().stream().filter(FieldMetadata::selectable).map(FieldMetadata::name)
            .findFirst().orElseThrow();
}
```

(This method exists purely so tests can exercise a resolved tree without the JSON layer. Mark it package-private, Javadoc it as test-only.)

- [ ] **Step 5: Run**

Run: `./mvnw -q -pl beanquery-core test -Dtest=DynamicQueryExecutorIT`
Expected: PASS.

- [ ] **Step 6: License + commit**

```bash
./mvnw -q license:format
git add beanquery-core
git commit -m "feat(core): add AlwaysFalse resolved-filter node"
```

---

## Task 7: `ReferenceFilterTranslator`

**Files:**
- Create: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/query/ReferenceFilterTranslator.java`
- Test: `beanquery-core/src/test/java/io/github/mszajner/beanquery/core/query/ReferenceFilterTranslatorTest.java` (create)

**Interfaces:**
- Consumes: `ReferenceResolvers`, `EntityMetadata`, `ResolvedFilterNode` (incl. `AlwaysFalse`), `FieldKind.REFERENCE`.
- Produces:
  - `final class ReferenceFilterTranslator` with `ReferenceFilterTranslator(ReferenceResolvers resolvers, int maxReferenceFilterIds)` and `ResolvedFilterNode translate(ResolvedFilterNode tree, EntityMetadata meta)`.
  - On any problem it throws `InvalidQueryException` (accumulated messages).
  - Rules per `Condition` whose `field().kind() == REFERENCE`:
    - `IS_NULL` / `IS_NOT_NULL` → `new Condition(idField, sameOp, null)`, no resolver call.
    - else → `resolver.resolveFilter(shortName, op, value)`:
      - `Optional.empty()` → error `"operator " + op + " is not supported for field '" + fieldName + "'"`
      - size `> maxReferenceFilterIds` → error `"filter on '" + fieldName + "' matches too many references (> " + max + "); narrow it"`
      - empty set → `new AlwaysFalse()`
      - else → `new Condition(idField, IN, List.copyOf(ids))`
  - `Group` children translated recursively; `AlwaysFalse` passes through.

- [ ] **Step 1: Write the failing tests** — `ReferenceFilterTranslatorTest`:

```java
package io.github.mszajner.beanquery.core.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldKind;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.metadata.ReferenceMetadata;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import io.github.mszajner.beanquery.core.reference.ReferenceResolvers;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReferenceFilterTranslatorTest {

    private final AtomicInteger resolveFilterCalls = new AtomicInteger();

    private final FieldMetadata idField = new FieldMetadata(
            "customerId", "customerId", Long.class, true, true, true,
            Set.of(FilterOperator.EQ, FilterOperator.IN, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL),
            FieldKind.COLUMN);

    private final FieldMetadata refName = new FieldMetadata(
            "customer.name", "", String.class, true, true, false,
            Set.of(FilterOperator.EQ, FilterOperator.ILIKE, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL),
            FieldKind.REFERENCE);

    private final EntityMetadata meta = new EntityMetadata(
            "order", Object.class, List.of(idField, refName),
            List.of(new ReferenceMetadata("customer", "customerId", List.of("name"))));

    private ReferenceFilterTranslator translator(Optional<Set<Object>> filterResult) {
        ReferenceResolver resolver = new ReferenceResolver() {
            public String referenceName() { return "customer"; }
            public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) { return Map.of(); }
            public Optional<Set<Object>> resolveFilter(String f, FilterOperator o, Object v) {
                resolveFilterCalls.incrementAndGet();
                return filterResult;
            }
        };
        return new ReferenceFilterTranslator(new ReferenceResolvers(List.of(resolver)), 1000);
    }

    @Test
    void referenceConditionBecomesIdInList() {
        ResolvedFilterNode out = translator(Optional.of(Set.of(1L, 2L)))
                .translate(new ResolvedFilterNode.Condition(refName, FilterOperator.ILIKE, "acme"), meta);

        ResolvedFilterNode.Condition c = (ResolvedFilterNode.Condition) out;
        assertThat(c.field().name()).isEqualTo("customerId");
        assertThat(c.op()).isEqualTo(FilterOperator.IN);
        assertThat((List<?>) c.value()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void emptyIdSetBecomesAlwaysFalse() {
        ResolvedFilterNode out = translator(Optional.of(Set.of()))
                .translate(new ResolvedFilterNode.Condition(refName, FilterOperator.ILIKE, "zzz"), meta);
        assertThat(out).isInstanceOf(ResolvedFilterNode.AlwaysFalse.class);
    }

    @Test
    void unsupportedOperatorIsA400() {
        assertThatThrownBy(() -> translator(Optional.empty())
                .translate(new ResolvedFilterNode.Condition(refName, FilterOperator.ILIKE, "x"), meta))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("customer.name");
    }

    @Test
    void tooManyIdsIsA400() {
        Set<Object> big = new java.util.HashSet<>();
        for (long i = 0; i < 5; i++) big.add(i);
        ReferenceFilterTranslator small = new ReferenceFilterTranslator(
                translator(Optional.of(big)).resolvers(), 3); // helper accessor, see impl note
        assertThatThrownBy(() -> small.translate(
                new ResolvedFilterNode.Condition(refName, FilterOperator.ILIKE, "x"), meta))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("narrow");
    }

    @Test
    void isNullMapsToIdColumnWithoutCallingResolver() {
        ResolvedFilterNode out = translator(Optional.empty())
                .translate(new ResolvedFilterNode.Condition(refName, FilterOperator.IS_NULL, null), meta);
        ResolvedFilterNode.Condition c = (ResolvedFilterNode.Condition) out;
        assertThat(c.field().name()).isEqualTo("customerId");
        assertThat(c.op()).isEqualTo(FilterOperator.IS_NULL);
        assertThat(resolveFilterCalls.get()).isZero();
    }
}
```

**Impl note:** drop the `.resolvers()` helper — instead give the test its own `ReferenceResolvers` variable. Rewrite `tooManyIdsIsA400` to build the resolver inline with `Optional.of(big)` and `new ReferenceFilterTranslator(resolvers, 3)`.

- [ ] **Step 2: Run — expect compile failure**

Run: `./mvnw -q -pl beanquery-core test -Dtest=ReferenceFilterTranslatorTest`
Expected: compile failure.

- [ ] **Step 3: Implement `ReferenceFilterTranslator`**

```java
package io.github.mszajner.beanquery.core.query;

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldKind;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.metadata.ReferenceMetadata;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import io.github.mszajner.beanquery.core.reference.ReferenceResolvers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Rewrites {@link ResolvedFilterNode.Condition}s on {@link FieldKind#REFERENCE}
 * fields into a local {@code idColumn IN (…)} condition (or {@link ResolvedFilterNode.AlwaysFalse}),
 * so the executor only ever sees local columns.
 */
final class ReferenceFilterTranslator {

    private final ReferenceResolvers resolvers;
    private final int maxReferenceFilterIds;

    ReferenceFilterTranslator(ReferenceResolvers resolvers, int maxReferenceFilterIds) {
        this.resolvers = resolvers;
        this.maxReferenceFilterIds = maxReferenceFilterIds;
    }

    ResolvedFilterNode translate(ResolvedFilterNode tree, EntityMetadata meta) {
        if (tree == null) {
            return null;
        }
        List<String> errors = new ArrayList<>();
        ResolvedFilterNode out = walk(tree, meta, errors);
        if (!errors.isEmpty()) {
            throw new InvalidQueryException(errors);
        }
        return out;
    }

    private ResolvedFilterNode walk(ResolvedFilterNode node, EntityMetadata meta, List<String> errors) {
        if (node instanceof ResolvedFilterNode.Group group) {
            List<ResolvedFilterNode> children = new ArrayList<>(group.children().size());
            for (ResolvedFilterNode child : group.children()) {
                children.add(walk(child, meta, errors));
            }
            return new ResolvedFilterNode.Group(group.logic(), children);
        }
        if (node instanceof ResolvedFilterNode.AlwaysFalse) {
            return node;
        }
        ResolvedFilterNode.Condition condition = (ResolvedFilterNode.Condition) node;
        if (condition.field().kind() != FieldKind.REFERENCE) {
            return condition;
        }
        return translateCondition(condition, meta, errors);
    }

    private ResolvedFilterNode translateCondition(
            ResolvedFilterNode.Condition condition, EntityMetadata meta, List<String> errors) {

        String fieldName = condition.field().name();
        ReferenceMetadata ref = meta.referenceForField(fieldName).orElseThrow(() ->
                new IllegalStateException("no ReferenceMetadata for reference field '" + fieldName + "'"));
        FieldMetadata idField = meta.field(ref.idFieldPath()).orElseThrow(() ->
                new IllegalStateException("reference id field '" + ref.idFieldPath() + "' is not registered"));

        FilterOperator op = condition.op();
        if (op == FilterOperator.IS_NULL || op == FilterOperator.IS_NOT_NULL) {
            return new ResolvedFilterNode.Condition(idField, op, null);
        }

        String shortName = fieldName.substring(ref.name().length() + 1);
        ReferenceResolver resolver = resolvers.require(ref.name());
        Optional<Set<Object>> ids = resolver.resolveFilter(shortName, op, condition.value());
        if (ids.isEmpty()) {
            errors.add("operator " + op + " is not supported for field '" + fieldName + "'");
            return new ResolvedFilterNode.AlwaysFalse();
        }
        Set<Object> matched = ids.get();
        if (matched.isEmpty()) {
            return new ResolvedFilterNode.AlwaysFalse();
        }
        if (matched.size() > maxReferenceFilterIds) {
            errors.add("filter on '" + fieldName + "' matches too many references (> "
                    + maxReferenceFilterIds + "); narrow it");
            return new ResolvedFilterNode.AlwaysFalse();
        }
        return new ResolvedFilterNode.Condition(idField, FilterOperator.IN, List.copyOf(matched));
    }
}
```

- [ ] **Step 4: Run**

Run: `./mvnw -q -pl beanquery-core test -Dtest=ReferenceFilterTranslatorTest`
Expected: PASS.

- [ ] **Step 5: License + commit**

```bash
./mvnw -q license:format
git add beanquery-core
git commit -m "feat(core): add ReferenceFilterTranslator"
```

---

## Task 8: `ReferenceEnricher`

**Files:**
- Create: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/query/ReferenceEnricher.java`
- Test: `beanquery-core/src/test/java/io/github/mszajner/beanquery/core/query/ReferenceEnricherTest.java` (create)

**Interfaces:**
- Consumes: `ReferenceResolvers`, `ReferenceMetadata`.
- Produces:
  - `record ReferenceEnricher.Selection(ReferenceMetadata reference, List<String> subFields, List<Object> idPerRow)` — `idPerRow` aligned index-for-index with the row list.
  - `final class ReferenceEnricher` with `ReferenceEnricher(ReferenceResolvers resolvers)` and `void enrich(List<Map<String,Object>> rows, List<Selection> selections)`.
  - Per selection: gather distinct non-null ids from `idPerRow`; if none, write `null` for every `"<ref>.<sub>"` key on every row; else **one** `resolve(ids, Set.copyOf(subFields))` call; per row write `row.put(ref.name()+"."+sub, valueOrNull)`.
  - Guarantee: at most one `resolve()` call per selection.

- [ ] **Step 1: Write the failing tests** — `ReferenceEnricherTest`:

```java
package io.github.mszajner.beanquery.core.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.metadata.ReferenceMetadata;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import io.github.mszajner.beanquery.core.reference.ReferenceResolvers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReferenceEnricherTest {

    private final AtomicInteger resolveCalls = new AtomicInteger();

    private ReferenceResolvers resolvers(Map<Object, Map<String, Object>> data) {
        ReferenceResolver r = new ReferenceResolver() {
            public String referenceName() { return "customer"; }
            public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) {
                resolveCalls.incrementAndGet();
                Map<Object, Map<String, Object>> out = new java.util.HashMap<>();
                ids.forEach(id -> { if (data.containsKey(id)) out.put(id, data.get(id)); });
                return out;
            }
            public Optional<Set<Object>> resolveFilter(String f, FilterOperator o, Object v) { return Optional.empty(); }
        };
        return new ReferenceResolvers(List.of(r));
    }

    @Test
    void oneResolveCallSplicesValuesAndNullsMissing() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("id", 1, "customer.name", null));
        rows.add(row("id", 2, "customer.name", null));
        rows.add(row("id", 3, "customer.name", null)); // id 3 -> customerId null
        rows.add(row("id", 4, "customer.name", null)); // id 4 -> customerId 99, absent from resolver

        ReferenceEnricher enricher = new ReferenceEnricher(
                resolvers(Map.of(10L, Map.of("name", "Acme"), 11L, Map.of("name", "Beta"))));

        enricher.enrich(rows, List.of(new ReferenceEnricher.Selection(
                new ReferenceMetadata("customer", "customerId", List.of("name")),
                List.of("name"),
                java.util.Arrays.asList(10L, 11L, null, 99L))));

        assertThat(resolveCalls.get()).isEqualTo(1);
        assertThat(rows.get(0)).containsEntry("customer.name", "Acme");
        assertThat(rows.get(1)).containsEntry("customer.name", "Beta");
        assertThat(rows.get(2)).containsEntry("customer.name", null);
        assertThat(rows.get(3)).containsEntry("customer.name", null);
    }

    @Test
    void noNonNullIdsMeansNoResolveCall() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(row("id", 1, "customer.name", null)));
        new ReferenceEnricher(resolvers(Map.of())).enrich(rows, List.of(new ReferenceEnricher.Selection(
                new ReferenceMetadata("customer", "customerId", List.of("name")),
                List.of("name"), java.util.Collections.singletonList(null))));
        assertThat(resolveCalls.get()).isZero();
        assertThat(rows.get(0)).containsEntry("customer.name", null);
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

Run: `./mvnw -q -pl beanquery-core test -Dtest=ReferenceEnricherTest`
Expected: compile failure.

- [ ] **Step 3: Implement `ReferenceEnricher`**

```java
package io.github.mszajner.beanquery.core.query;

import io.github.mszajner.beanquery.core.metadata.ReferenceMetadata;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import io.github.mszajner.beanquery.core.reference.ReferenceResolvers;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills {@code REFERENCE} row keys via a single batched {@link ReferenceResolver#resolve}
 * call per reference per page.
 */
final class ReferenceEnricher {

    /**
     * @param reference the reference being enriched
     * @param subFields the sub-field short names requested in {@code select}
     * @param idPerRow  the local id value for each row, index-aligned with the row list
     *                  ({@code null} where the row's FK column is null)
     */
    record Selection(ReferenceMetadata reference, List<String> subFields, List<Object> idPerRow) {
    }

    private final ReferenceResolvers resolvers;

    ReferenceEnricher(ReferenceResolvers resolvers) {
        this.resolvers = resolvers;
    }

    void enrich(List<Map<String, Object>> rows, List<Selection> selections) {
        for (Selection selection : selections) {
            enrichOne(rows, selection);
        }
    }

    private void enrichOne(List<Map<String, Object>> rows, Selection selection) {
        String prefix = selection.reference().name() + ".";
        Set<Object> ids = new LinkedHashSet<>();
        for (Object id : selection.idPerRow()) {
            if (id != null) {
                ids.add(id);
            }
        }

        Map<Object, Map<String, Object>> resolved = ids.isEmpty()
                ? Map.of()
                : resolvers.require(selection.reference().name())
                        .resolve(ids, Set.copyOf(selection.subFields()));

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            Object id = selection.idPerRow().get(i);
            Map<String, Object> values = id == null ? null : resolved.get(id);
            for (String sub : selection.subFields()) {
                row.put(prefix + sub, values == null ? null : values.get(sub));
            }
        }
    }
}
```

- [ ] **Step 4: Run**

Run: `./mvnw -q -pl beanquery-core test -Dtest=ReferenceEnricherTest`
Expected: PASS.

- [ ] **Step 5: License + commit**

```bash
./mvnw -q license:format
git add beanquery-core
git commit -m "feat(core): add ReferenceEnricher"
```

---

## Task 9: Wire translator + enricher into `DynamicQueryExecutor`

**Files:**
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/query/DynamicQueryExecutor.java`
- Test: `beanquery-core/src/test/java/io/github/mszajner/beanquery/core/executor/ReferenceExecutorIT.java` (create — H2, `@DataJpaTest` like `DynamicQueryExecutorIT`)

**Interfaces:**
- Consumes: `ReferenceFilterTranslator`, `ReferenceEnricher`, `ReferenceResolvers`, `EntityMetadata.references()`, `FieldKind`.
- Produces:
  - New constructor `DynamicQueryExecutor(EntityManager, FilterValueConverter, ReferenceResolvers, int maxReferenceFilterIds)`.
  - Existing `DynamicQueryExecutor(EntityManager, FilterValueConverter)` delegates with `ReferenceResolvers.EMPTY, 1000`.
  - Behaviour: translate filters before row+count; add reference id columns to selection when a `REFERENCE` field is selected; enrich rows after fetch; never resolve a `REFERENCE` path in Criteria; `select` order preserved.

- [ ] **Step 1: Write the failing IT** — `ReferenceExecutorIT`. Mirror `DynamicQueryExecutorIT`'s `@DataJpaTest` + `Config` pattern. Entities: a JPA `RefOrder` with a `Long customerId` column and NO association; a `Customer` row set inserted with `TestEntityManager` into a separate table; a spy resolver reading it.

```java
package io.github.mszajner.beanquery.core.executor;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldKind;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.metadata.ReferenceMetadata;
import io.github.mszajner.beanquery.core.query.DynamicQueryExecutor;
import io.github.mszajner.beanquery.core.query.FilterValueConverter;
import io.github.mszajner.beanquery.core.query.Page;
import io.github.mszajner.beanquery.core.query.QueryRequest;
import io.github.mszajner.beanquery.core.query.QueryResult;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import io.github.mszajner.beanquery.core.reference.ReferenceResolvers;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ReferenceExecutorIT {

    @Autowired
    private EntityManager em;

    private final AtomicInteger resolveCalls = new AtomicInteger();
    private DynamicQueryExecutor executor;

    // -- fixture metadata ------------------------------------------------
    private final FieldMetadata id = new FieldMetadata("id", "id", Long.class,
            true, true, true, Set.of(FilterOperator.EQ), FieldKind.COLUMN);
    private final FieldMetadata customerId = new FieldMetadata("customerId", "customerId", Long.class,
            true, true, true, Set.of(FilterOperator.EQ, FilterOperator.IN, FilterOperator.IS_NULL), FieldKind.COLUMN);
    private final FieldMetadata custName = new FieldMetadata("customer.name", "", String.class,
            true, true, false, Set.of(FilterOperator.ILIKE, FilterOperator.IS_NULL), FieldKind.REFERENCE);
    private final EntityMetadata meta = new EntityMetadata("refOrder", RefOrder.class,
            List.of(id, customerId, custName),
            List.of(new ReferenceMetadata("customer", "customerId", List.of("name"))));

    @BeforeEach
    void setUp() {
        em.persist(new RefOrder(1L, 10L));
        em.persist(new RefOrder(2L, 11L));
        em.persist(new RefOrder(3L, null));
        em.flush();

        ReferenceResolver resolver = new ReferenceResolver() {
            public String referenceName() { return "customer"; }
            public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) {
                resolveCalls.incrementAndGet();
                Map<Object, Map<String, Object>> data = Map.of(
                        10L, Map.of("name", "Acme"),
                        11L, Map.of("name", "Beta"));
                Map<Object, Map<String, Object>> out = new java.util.HashMap<>();
                ids.forEach(i -> { if (data.containsKey(i)) out.put(i, data.get(i)); });
                return out;
            }
            public Optional<Set<Object>> resolveFilter(String f, FilterOperator o, Object v) {
                if (o != FilterOperator.ILIKE) return Optional.empty();
                String needle = ((String) v).toLowerCase();
                Set<Object> ids = new java.util.HashSet<>();
                if ("acme".contains(needle)) ids.add(10L);
                if ("beta".contains(needle)) ids.add(11L);
                return Optional.of(ids);
            }
        };
        executor = new DynamicQueryExecutor(em, FilterValueConverter.withDefaultConversionService(),
                new ReferenceResolvers(List.of(resolver)), 1000);
    }

    @Test
    void selectEnrichesReferenceFieldsWithOneResolveCall() {
        QueryResult result = executor.execute(meta, new QueryRequest(
                List.of("id", "customer.name"), null, List.of(), new Page(0, 10)));

        assertThat(resolveCalls.get()).isEqualTo(1);
        assertThat(result.rows()).extracting(r -> r.get("customer.name"))
                .containsExactlyInAnyOrder("Acme", "Beta", null);
        assertThat(result.rows().get(0)).doesNotContainKey("customerId"); // id column stays internal
        assertThat(result.rows().get(0).keySet()).containsExactly("id", "customer.name"); // select order
    }

    @Test
    void filterOnReferenceFieldNarrowsRowsAndCount() {
        QueryResult result = executor.execute(meta, new QueryRequest(
                List.of("id"),
                new io.github.mszajner.beanquery.core.query.ConditionNode(
                        "customer.name", FilterOperator.ILIKE,
                        new tools.jackson.databind.node.TextNode("acme")),
                List.of(), new Page(0, 10)));

        assertThat(result.page().totalElements()).isEqualTo(1);
        assertThat(result.rows()).extracting(r -> r.get("id")).containsExactly(1L);
    }

    @Test
    void filterResolvingToNoIdsReturnsZeroRowsAndZeroTotal() {
        QueryResult result = executor.execute(meta, new QueryRequest(
                List.of("id"),
                new io.github.mszajner.beanquery.core.query.ConditionNode(
                        "customer.name", FilterOperator.ILIKE,
                        new tools.jackson.databind.node.TextNode("nomatch")),
                List.of(), new Page(0, 10)));
        assertThat(result.rows()).isEmpty();
        assertThat(result.page().totalElements()).isZero();
    }
}
```

Create the fixture entity `beanquery-core/src/test/java/io/github/mszajner/beanquery/core/executor/RefOrder.java`:

```java
package io.github.mszajner.beanquery.core.executor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class RefOrder {

    @Id
    private Long id;
    private Long customerId;

    protected RefOrder() {
    }

    public RefOrder(Long id, Long customerId) {
        this.id = id;
        this.customerId = customerId;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }
}
```

(`FilterValueConverter` will convert the `customer.name` value against `String.class` — `custName.javaType()` is `String`, good. `ConditionNode` takes a Jackson `JsonNode`; `TextNode` is `tools.jackson.databind.node.TextNode`.)

- [ ] **Step 2: Run — expect compile failure** (4-arg constructor missing)

Run: `./mvnw -q -pl beanquery-core test -Dtest=ReferenceExecutorIT`
Expected: compile failure.

- [ ] **Step 3: Add the constructor + fields**

```java
private final FilterValueConverter valueConverter;
private final ReferenceFilterTranslator referenceFilterTranslator;
private final ReferenceEnricher referenceEnricher;

public DynamicQueryExecutor(EntityManager entityManager, FilterValueConverter valueConverter) {
    this(entityManager, valueConverter, ReferenceResolvers.EMPTY, 1000);
}

public DynamicQueryExecutor(EntityManager entityManager, FilterValueConverter valueConverter,
        ReferenceResolvers referenceResolvers, int maxReferenceFilterIds) {
    this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    this.valueConverter = Objects.requireNonNull(valueConverter, "valueConverter");
    Objects.requireNonNull(referenceResolvers, "referenceResolvers");
    this.referenceFilterTranslator = new ReferenceFilterTranslator(referenceResolvers, maxReferenceFilterIds);
    this.referenceEnricher = new ReferenceEnricher(referenceResolvers);
}
```

Import `io.github.mszajner.beanquery.core.reference.ReferenceResolvers`.

- [ ] **Step 4: Translate filters in `execute(...)`**

After `ResolvedFilterNode filters = withMandatory(...)`:

```java
filters = referenceFilterTranslator.translate(filters, meta);
```

- [ ] **Step 5: Rework `fetchRows` for reference selection + enrichment**

Replace `fetchRows` with a version that splits select fields, adds internal id columns, and calls the enricher:

```java
private List<Map<String, Object>> fetchRows(CriteriaBuilder cb, EntityMetadata meta,
        List<FieldMetadata> selectFields, ResolvedFilterNode filters, List<Sort> sort, Page page) {

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<Object> root = cq.from(entityClass(meta));
    PathResolver paths = new PathResolver(root);

    // 1. Criteria selections: non-REFERENCE fields, then internal id columns for referenced selects
    List<FieldMetadata> columnSelects = selectFields.stream()
            .filter(f -> f.kind() != FieldKind.REFERENCE).toList();

    List<Selection<?>> selections = new ArrayList<>();
    for (FieldMetadata field : columnSelects) {
        selections.add(paths.resolve(field.path()));
    }

    // references present in select -> ensure their id column is fetched
    List<ReferenceMetadata> selectedRefs = referencesInSelect(meta, selectFields);
    Map<String, Integer> idColumnIndex = new LinkedHashMap<>();
    for (ReferenceMetadata ref : selectedRefs) {
        int existing = indexOfPath(columnSelects, ref.idFieldPath());
        if (existing >= 0) {
            idColumnIndex.put(ref.name(), existing);
        } else {
            selections.add(paths.resolve(ref.idFieldPath()));
            idColumnIndex.put(ref.name(), selections.size() - 1);
        }
    }

    cq.select(cb.tuple(selections.toArray(new Selection<?>[0])));

    Predicate where = toPredicate(cb, paths, filters);
    if (where != null) {
        cq.where(where);
    }
    cq.orderBy(buildOrder(cb, paths, root, meta, sort));

    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    query.setFirstResult((int) Math.min((long) page.number() * page.size(), Integer.MAX_VALUE));
    query.setMaxResults(page.size());
    List<Tuple> tuples = query.getResultList();

    // 2. assemble rows in select order (REFERENCE keys as null placeholders)
    List<Map<String, Object>> rows = new ArrayList<>(tuples.size());
    for (Tuple tuple : tuples) {
        Map<String, Object> row = new LinkedHashMap<>();
        int col = 0;
        for (FieldMetadata field : selectFields) {
            if (field.kind() == FieldKind.REFERENCE) {
                row.put(field.name(), null);
            } else {
                row.put(field.name(), tuple.get(col++));
            }
        }
        rows.add(row);
    }

    // 3. enrich
    if (!selectedRefs.isEmpty()) {
        List<ReferenceEnricher.Selection> enrichSelections = new ArrayList<>();
        for (ReferenceMetadata ref : selectedRefs) {
            int idx = idColumnIndex.get(ref.name());
            List<Object> idPerRow = tuples.stream().map(t -> t.get(idx)).toList();
            List<String> subs = subFieldsInSelect(ref, selectFields);
            enrichSelections.add(new ReferenceEnricher.Selection(ref, subs, idPerRow));
        }
        referenceEnricher.enrich(rows, enrichSelections);
    }
    return rows;
}

private static List<ReferenceMetadata> referencesInSelect(EntityMetadata meta, List<FieldMetadata> selectFields) {
    LinkedHashMap<String, ReferenceMetadata> refs = new LinkedHashMap<>();
    for (FieldMetadata f : selectFields) {
        if (f.kind() == FieldKind.REFERENCE) {
            meta.referenceForField(f.name()).ifPresent(r -> refs.putIfAbsent(r.name(), r));
        }
    }
    return List.copyOf(refs.values());
}

private static List<String> subFieldsInSelect(ReferenceMetadata ref, List<FieldMetadata> selectFields) {
    List<String> subs = new ArrayList<>();
    String prefix = ref.name() + ".";
    for (FieldMetadata f : selectFields) {
        if (f.kind() == FieldKind.REFERENCE && f.name().startsWith(prefix)) {
            subs.add(f.name().substring(prefix.length()));
        }
    }
    return subs;
}

private static int indexOfPath(List<FieldMetadata> fields, String path) {
    for (int i = 0; i < fields.size(); i++) {
        if (fields.get(i).path().equals(path)) {
            return i;
        }
    }
    return -1;
}
```

Add imports for `FieldKind`, `ReferenceMetadata`.

- [ ] **Step 6: Keep `resolveSelect` intact**

`resolveSelect` already maps each `select` name to a `FieldMetadata` via `requireField`. A `REFERENCE` field resolves fine (it's registered). No change — but confirm `buildOrder` never receives a `REFERENCE` field: it's guarded by the validator's `sortable` check upstream; the executor's `buildOrder` calls `requireField` then `paths.resolve(field.path())`. Add a defensive guard:

```java
// in buildOrder, after FieldMetadata field = requireField(meta, clause.field());
if (field.kind() == FieldKind.REFERENCE) {
    throw new InvalidQueryException(List.of("sort: field '" + field.name() + "' is not sortable"));
}
```

- [ ] **Step 7: Run**

Run: `./mvnw -q -pl beanquery-core test -Dtest=ReferenceExecutorIT,DynamicQueryExecutorIT`
Expected: PASS.

- [ ] **Step 8: Full core module**

Run: `./mvnw -q -pl beanquery-core test`
Expected: PASS.

- [ ] **Step 9: License + commit**

```bash
./mvnw -q license:format
git add beanquery-core
git commit -m "feat(core): translate reference filters and enrich rows in the executor"
```

---

## Task 10: `/metadata` exposes `kind`

**Files:**
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/web/MetadataResponse.java`
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/web/MetadataMapper.java`
- Test: `beanquery-core/src/test/java/io/github/mszajner/beanquery/core/web/BeanQueryControllerTest.java`

**Interfaces:**
- Produces: `MetadataResponse.FieldDescriptor` gains `String kind` (after `operators`). `MetadataMapper.describe` sets `field.kind().name()`.

- [ ] **Step 1: Write the failing test** — add to `BeanQueryControllerTest` (metadata test around line 104), assert a reference field reports `kind` and `sortable:false`:

```java
@Test
void metadataReportsFieldKind() throws Exception {
    // fixture entity/metadata in this test must include a REFERENCE field named "customer.name"
    mvc.perform(get("/api/bq/order/metadata"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fields[?(@.name == 'customer.name')].kind", hasItem("REFERENCE")))
            .andExpect(jsonPath("$.fields[?(@.name == 'customer.name')].sortable", hasItem(false)))
            .andExpect(jsonPath("$.fields[?(@.name == 'id')].kind", hasItem("COLUMN")));
}
```

If `BeanQueryControllerTest` has no reference fixture, add one to its stub `EntityMetadata` (it builds metadata by hand — add a `FieldMetadata("customer.name", "", String.class, true, true, false, <ops>, FieldKind.REFERENCE)` and a `ReferenceMetadata` to the `EntityMetadata`).

- [ ] **Step 2: Run — expect compile failure**

Run: `./mvnw -q -pl beanquery-core test -Dtest=BeanQueryControllerTest#metadataReportsFieldKind`
Expected: compile failure.

- [ ] **Step 3: Add `kind` to `FieldDescriptor`**

```java
public record FieldDescriptor(
        String name,
        String type,
        List<String> values,
        boolean selectable,
        boolean filterable,
        boolean sortable,
        List<String> operators,
        String kind) {
}
```

Update the record Javadoc.

- [ ] **Step 4: Populate it in `MetadataMapper.describe`**

```java
return new FieldDescriptor(field.name(), type, values,
        field.selectable(), field.filterable(), field.sortable(), operators, field.kind().name());
```

- [ ] **Step 5: Run**

Run: `./mvnw -q -pl beanquery-core test -Dtest=BeanQueryControllerTest`
Expected: PASS.

- [ ] **Step 6: License + commit**

```bash
./mvnw -q license:format
git add beanquery-core
git commit -m "feat(core): expose field kind in the /metadata response"
```

---

## Task 11: `QueryAuthorizer` — mandatory filters on reference fields

**Files:**
- Modify: `beanquery-core/src/main/java/io/github/mszajner/beanquery/core/security/QueryAuthorizationService.java`
- Test: `beanquery-core/src/test/java/io/github/mszajner/beanquery/core/security/QueryAuthorizationServiceTest.java`

**Interfaces:**
- Produces: `QueryAuthorizationService.resolve(MandatoryFilter, EntityMetadata, QueryAuthorizer)` — when the target `FieldMetadata.kind() == REFERENCE`, return `new ResolvedFilterNode.Condition(referenceField, filter.op(), filter.value())` **without** the `field.allows(op)` check (the executor's `ReferenceFilterTranslator` validates and translates it). Column fields keep today's behaviour (unknown field / disallowed operator → `QueryAuthorizerConfigurationException` → 500).

- [ ] **Step 1: Write the failing test** — add to `QueryAuthorizationServiceTest`:

```java
@Test
void mandatoryFilterOnAReferenceFieldIsPassedThroughUnresolved() {
    FieldMetadata refField = new FieldMetadata("customer.tier", "", String.class,
            true, true, false,
            Set.of(FilterOperator.EQ, FilterOperator.IS_NULL), FieldKind.REFERENCE);
    EntityMetadata meta = new EntityMetadata("order", Object.class, List.of(refField),
            List.of(new ReferenceMetadata("customer", "customerId", List.of("tier"))));

    QueryAuthorizer authorizer = fixedAuthorizer(QueryAuthorization.allowWith(
            new MandatoryFilter("customer.tier", FilterOperator.EQ, "gold")));

    AppliedAuthorization applied = new QueryAuthorizationService(List.of(authorizer)).authorize(meta, null);

    assertThat(applied.mandatoryPredicates()).singleElement()
            .isInstanceOfSatisfying(ResolvedFilterNode.Condition.class, c -> {
                assertThat(c.field().name()).isEqualTo("customer.tier");
                assertThat(c.op()).isEqualTo(FilterOperator.EQ);
                assertThat(c.value()).isEqualTo("gold");
            });
}
```

(`fixedAuthorizer(...)` — reuse the test's existing helper for a stub authorizer, or add a tiny inline one.)

- [ ] **Step 2: Run — expect FAIL** (today it validates `allows(op)` and would build a resolved condition; `customer.tier` REFERENCE ops include EQ so it may pass — adjust the assertion to check it is *not* pre-resolved: the current code returns the same `Condition` shape, so make the test meaningful by asserting the field kept is the REFERENCE field, and add a negative case where `op` is `ILIKE` which is not in the ref field's `allowedOperators` but must still pass through):

```java
@Test
void mandatoryReferenceFilterWithOperatorOutsideMetadataStillPassesThrough() {
    FieldMetadata refField = new FieldMetadata("customer.tier", "", String.class,
            true, true, false, Set.of(FilterOperator.EQ), FieldKind.REFERENCE);
    EntityMetadata meta = new EntityMetadata("order", Object.class, List.of(refField),
            List.of(new ReferenceMetadata("customer", "customerId", List.of("tier"))));
    QueryAuthorizer authorizer = fixedAuthorizer(QueryAuthorization.allowWith(
            new MandatoryFilter("customer.tier", FilterOperator.ILIKE, "go")));

    AppliedAuthorization applied = new QueryAuthorizationService(List.of(authorizer)).authorize(meta, null);

    assertThat(applied.mandatoryPredicates()).singleElement()
            .isInstanceOfSatisfying(ResolvedFilterNode.Condition.class,
                    c -> assertThat(c.op()).isEqualTo(FilterOperator.ILIKE));
}
```

Run: `./mvnw -q -pl beanquery-core test -Dtest=QueryAuthorizationServiceTest`
Expected: FAIL — current `resolve` throws `QueryAuthorizerConfigurationException` because `ILIKE` is not in `allowedOperators`.

- [ ] **Step 3: Branch on `kind` in `QueryAuthorizationService.resolve`**

```java
private ResolvedFilterNode resolve(MandatoryFilter filter, EntityMetadata meta, QueryAuthorizer authorizer) {
    FieldMetadata field = meta.field(filter.field()).orElse(null);
    if (field == null) {
        throw configurationError(authorizer, meta, "unknown field '" + filter.field() + "'");
    }
    if (field.kind() == FieldKind.REFERENCE) {
        // the executor's ReferenceFilterTranslator validates the operator and resolves the value
        return new ResolvedFilterNode.Condition(field, filter.op(), filter.value());
    }
    if (filter.op() == null || !field.allows(filter.op())) {
        throw configurationError(authorizer, meta, "operator " + filter.op() + " is not allowed for field '"
                + filter.field() + "' (allowed: " + field.allowedOperators() + ")");
    }
    return new ResolvedFilterNode.Condition(field, filter.op(), filter.value());
}
```

Import `FieldKind`.

- [ ] **Step 4: Run**

Run: `./mvnw -q -pl beanquery-core test -Dtest=QueryAuthorizationServiceTest`
Expected: PASS.

- [ ] **Step 5: License + commit**

```bash
./mvnw -q license:format
git add beanquery-core
git commit -m "feat(core): pass reference mandatory filters through to the translator"
```

---

## Task 12: Starter — property + autoconfiguration wiring

**Files:**
- Modify: `beanquery-starter/src/main/java/io/github/mszajner/beanquery/starter/BeanQueryProperties.java`
- Modify: `beanquery-starter/src/main/java/io/github/mszajner/beanquery/starter/BeanQueryAutoConfiguration.java`
- Test: `beanquery-starter/src/test/java/io/github/mszajner/beanquery/starter/BeanQueryAutoConfigurationTest.java`

**Interfaces:**
- Produces:
  - `BeanQueryProperties.getMaxReferenceFilterIds()` / setter; default `1000`.
  - `@Bean ReferenceResolvers beanQueryReferenceResolvers(ObjectProvider<ReferenceResolver>)`.
  - `beanQueryEntityRegistry(EntityManagerFactory, ReferenceResolvers)` — passes `resolvers.names()`.
  - `beanQueryExecutor(EntityManager, FilterValueConverter, ReferenceResolvers, BeanQueryProperties)`.

- [ ] **Step 1: Write the failing tests** — add to `BeanQueryAutoConfigurationTest`:

```java
@Test
void referenceFilterIdLimitDefaultsAndIsOverridable() {
    runner.run(context ->
            assertThat(context.getBean(BeanQueryProperties.class).getMaxReferenceFilterIds()).isEqualTo(1000));
    runner.withPropertyValues("beanquery.max-reference-filter-ids=25").run(context ->
            assertThat(context.getBean(BeanQueryProperties.class).getMaxReferenceFilterIds()).isEqualTo(25));
}

@Test
void referenceResolversBeanIsRegistered() {
    runner.run(context -> assertThat(context).hasSingleBean(
            io.github.mszajner.beanquery.core.reference.ReferenceResolvers.class));
}
```

Also add `.hasSingleBean(ReferenceResolvers.class)` to `registersTheStackByDefault`.

- [ ] **Step 2: Run — expect FAIL**

Run: `./mvnw -q -pl beanquery-starter test -Dtest=BeanQueryAutoConfigurationTest`
Expected: FAIL (`getMaxReferenceFilterIds` missing, no `ReferenceResolvers` bean).

- [ ] **Step 3: Add the property**

```java
/** Largest id set a {@code ReferenceResolver.resolveFilter} may return before the request is rejected. */
private int maxReferenceFilterIds = 1000;

public int getMaxReferenceFilterIds() {
    return maxReferenceFilterIds;
}

public void setMaxReferenceFilterIds(int maxReferenceFilterIds) {
    this.maxReferenceFilterIds = maxReferenceFilterIds;
}
```

- [ ] **Step 4: Wire the auto-configuration**

```java
@Bean
@ConditionalOnMissingBean
public ReferenceResolvers beanQueryReferenceResolvers(ObjectProvider<ReferenceResolver> resolvers) {
    return new ReferenceResolvers(resolvers.orderedStream().toList());
}

@Bean
@ConditionalOnMissingBean
public QueryableEntityRegistry beanQueryEntityRegistry(EntityManagerFactory entityManagerFactory,
        ReferenceResolvers referenceResolvers) {
    return new QueryableEntityRegistry(entityManagerFactory, referenceResolvers.names());
}

@Bean
@ConditionalOnMissingBean
public DynamicQueryExecutor beanQueryExecutor(EntityManager entityManager, FilterValueConverter valueConverter,
        ReferenceResolvers referenceResolvers, BeanQueryProperties properties) {
    return new DynamicQueryExecutor(entityManager, valueConverter,
            referenceResolvers, properties.getMaxReferenceFilterIds());
}
```

Imports: `io.github.mszajner.beanquery.core.reference.ReferenceResolver`, `...ReferenceResolvers`.

- [ ] **Step 5: Run the starter module**

Run: `./mvnw -q -pl beanquery-starter test -Dtest=BeanQueryAutoConfigurationTest`
Expected: PASS.

- [ ] **Step 6: Validate `maxReferenceFilterIds >= 1`**

The executor accepts any int today. Add the guard where the other limits are validated — in `BeanQueryProperties` there is none; `QueryRequestValidator` guards its own. Add a check in `DynamicQueryExecutor`'s 4-arg constructor:

```java
if (maxReferenceFilterIds < 1) {
    throw new IllegalArgumentException("maxReferenceFilterIds must be >= 1, was " + maxReferenceFilterIds);
}
```

Add a core unit test `DynamicQueryExecutorTest` (create, plain unit) asserting the `IllegalArgumentException`. Run it. Commit.

- [ ] **Step 7: License + commit**

```bash
./mvnw -q license:format
git add beanquery-core beanquery-starter
git commit -m "feat(starter): wire ReferenceResolvers and max-reference-filter-ids"
```

---

## Task 13: Starter integration test (H2, two simulated modules)

**Files:**
- Create: `beanquery-starter/src/test/java/io/github/mszajner/beanquery/starter/it/RefOrder.java` (entity, `customerId` column, no association, `@QueryableReference`)
- Create: `beanquery-starter/src/test/java/io/github/mszajner/beanquery/starter/it/CountingCustomerResolver.java` (spy `ReferenceResolver` over a `customers` table via `JdbcTemplate`)
- Create: `beanquery-starter/src/test/java/io/github/mszajner/beanquery/starter/it/ReferenceResolverIT.java`
- Test: the IT itself

**Interfaces:**
- Consumes: everything from Tasks 1–12 through the running auto-configuration.

- [ ] **Step 1: Write the entity + resolver fixtures**

`RefOrder.java`:

```java
package io.github.mszajner.beanquery.starter.it;

import io.github.mszajner.beanquery.core.annotation.Queryable;
import io.github.mszajner.beanquery.core.annotation.QueryableField;
import io.github.mszajner.beanquery.core.annotation.QueryableReference;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "RefOrder")
@Table(name = "ref_order")
@Queryable(name = "refOrder")
public class RefOrder {

    @Id
    @QueryableField
    private Long id;

    @QueryableField
    private String status;

    @QueryableField
    @QueryableReference(name = "customer", fields = {"name", "tier"},
            operators = {FilterOperator.EQ, FilterOperator.ILIKE})
    private Long customerId;

    protected RefOrder() {
    }

    public RefOrder(Long id, String status, Long customerId) {
        this.id = id;
        this.status = status;
        this.customerId = customerId;
    }
}
```

`CountingCustomerResolver.java`:

```java
package io.github.mszajner.beanquery.starter.it;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads the {@code customers} table directly — no JPA association from {@code ref_order}. */
public class CountingCustomerResolver implements ReferenceResolver {

    public final AtomicInteger resolveCalls = new AtomicInteger();
    public final AtomicInteger resolveFilterCalls = new AtomicInteger();

    private final JdbcTemplate jdbc;

    public CountingCustomerResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String referenceName() {
        return "customer";
    }

    @Override
    public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) {
        resolveCalls.incrementAndGet();
        Map<Object, Map<String, Object>> out = new HashMap<>();
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        jdbc.query("SELECT id, name, tier FROM customers WHERE id IN (" + placeholders + ")",
                ids.toArray(),
                rs -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("tier", rs.getString("tier"));
                    out.put(rs.getLong("id"), row);
                });
        return out;
    }

    @Override
    public Optional<Set<Object>> resolveFilter(String field, FilterOperator op, Object value) {
        resolveFilterCalls.incrementAndGet();
        if (!"name".equals(field) || op != FilterOperator.ILIKE) {
            return Optional.empty();
        }
        String needle = "%" + ((String) value).toLowerCase(Locale.ROOT) + "%";
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM customers WHERE LOWER(name) LIKE ?", Long.class, needle);
        return Optional.of(new HashSet<>(ids));
    }
}
```

- [ ] **Step 2: Write the IT**

`ReferenceResolverIT.java` — model on `QueryAuthorizerIT` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@Transactional`, `@Import(Config.class)`). The `customers` table is created and seeded in `@BeforeEach` via `JdbcTemplate` (H2, `create-drop` won't manage it — create it explicitly).

```java
package io.github.mszajner.beanquery.starter.it;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(ReferenceResolverIT.Config.class)
class ReferenceResolverIT {

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CountingCustomerResolver resolver;

    @BeforeEach
    void seed() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS customers (id BIGINT PRIMARY KEY, name VARCHAR(64), tier VARCHAR(16))");
        jdbc.update("DELETE FROM customers");
        jdbc.update("INSERT INTO customers VALUES (10,'Acme','gold'),(11,'Beta','silver'),(12,'Ceres','gold')");

        em.persist(new RefOrder(1L, "NEW", 10L));
        em.persist(new RefOrder(2L, "NEW", 11L));
        em.persist(new RefOrder(3L, "PAID", 10L));
        em.persist(new RefOrder(4L, "NEW", null));
        em.persist(new RefOrder(5L, "PAID", 99L)); // customer row does not exist
        em.flush();
        em.clear();
        resolver.resolveCalls.set(0);
        resolver.resolveFilterCalls.set(0);
    }

    @Test
    void selectEnrichesFromTheCustomersTableWithOneResolveCall() throws Exception {
        mvc.perform(post("/api/bq/refOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id","customer.name"],"sort":[{"field":"id","direction":"ASC"}],
                         "page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.id == 1)].['customer.name']", contains("Acme")))
                .andExpect(jsonPath("$.rows[?(@.id == 4)].['customer.name']", contains((Object) null)))
                .andExpect(jsonPath("$.rows[?(@.id == 5)].['customer.name']", contains((Object) null)));

        Assertions.assertThat(resolver.resolveCalls.get()).isEqualTo(1);
    }

    @Test
    void filterOnReferenceFieldReturnsOnlyMatchingOrdersWithCorrectTotal() throws Exception {
        mvc.perform(post("/api/bq/refOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"filters":{"field":"customer.name","op":"ILIKE","value":"acme"},
                         "sort":[{"field":"id","direction":"ASC"}],"page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.rows[*].id", equalTo(java.util.List.of(1, 3))));
    }

    @Test
    void filterResolvingToNoIdsYieldsZeroRowsAndZeroTotal() throws Exception {
        mvc.perform(post("/api/bq/refOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"filters":{"field":"customer.name","op":"ILIKE","value":"zzz"},
                         "page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.rows.length()").value(0));
    }

    @Test
    void sortOnReferenceFieldIs400() throws Exception {
        mvc.perform(post("/api/bq/refOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"sort":[{"field":"customer.name","direction":"ASC"}],
                         "page":{"number":0,"size":10}}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void referenceFilterInsideOrOnlyConstrainsItsBranch() throws Exception {
        // (customer.name ILIKE "acme" OR status EQ "NEW") -> orders 1,3 (acme) + 2,4 (NEW) = 1,2,3,4
        mvc.perform(post("/api/bq/refOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"filters":{"logic":"or","children":[
                           {"field":"customer.name","op":"ILIKE","value":"acme"},
                           {"field":"status","op":"EQ","value":"NEW"}]},
                         "sort":[{"field":"id","direction":"ASC"}],"page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[*].id", equalTo(java.util.List.of(1, 2, 3, 4))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        CountingCustomerResolver customerResolver(JdbcTemplate jdbc) {
            return new CountingCustomerResolver(jdbc);
        }
    }
}
```

**Note:** `RefOrder` must be picked up by the starter test app's entity scan. Check `beanquery-starter/src/test/.../it/TestApp.java` — add `RefOrder` package to its scan if it restricts (it likely `@SpringBootApplication` at `it` package, so it is covered). The `customers` table is not a JPA entity here — deliberately, to prove the resolver reaches outside JPA.

- [ ] **Step 3: Run**

Run: `./mvnw -q -pl beanquery-starter verify -Dit.test=ReferenceResolverIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 4: License + commit**

```bash
./mvnw -q license:format
git add beanquery-starter
git commit -m "test(starter): end-to-end reference resolver IT over an external table"
```

---

## Task 14: Parent POM — Spring Modulith BOM

**Files:**
- Modify: `pom.xml` (`dependencyManagement`, `properties`)

**Interfaces:**
- Produces: `spring-modulith-bom` `2.2.0-M1` imported in `dependencyManagement`.

- [ ] **Step 1: Add the property**

In `<properties>`:

```xml
<spring-modulith.version>2.2.0-M1</spring-modulith.version>
```

- [ ] **Step 2: Import the BOM** — in `<dependencyManagement><dependencies>`, after `spring-boot-dependencies`:

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-bom</artifactId>
    <version>${spring-modulith.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

- [ ] **Step 3: Verify it resolves against Boot 4.1.1**

Run: `./mvnw -q -ntp -pl beanquery-demo dependency:tree -Dincludes=org.springframework.modulith`
Expected: after Task 15 adds the demo dependency this prints the tree; for now run:
`./mvnw -q -ntp validate` — Expected: BUILD SUCCESS (no version conflict on import).

**Contingency:** if a later task shows `ApplicationModules.verify()` failing for reasons that trace to Boot 4.1 incompatibility (not a real module violation), STOP and report to the maintainer. Fallback is packages-only + an ArchUnit rule; do not switch silently.

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "build: import spring-modulith-bom 2.2.0-M1"
```

---

## Task 15: Demo — restructure into modules + Order/Customer/Resolver

**Files:**
- Modify: `beanquery-demo/pom.xml` (add `spring-modulith-starter-test` test scope, `spring-modulith-starter-core`)
- Move: `beanquery-demo/src/main/java/io/github/mszajner/beanquery/demo/Product.java` → `.../demo/product/Product.java`
- Move: `.../demo/Category.java` → `.../demo/product/Category.java`
- Create: `.../demo/order/CustomerOrder.java`
- Create: `.../demo/customer/Customer.java`
- Create: `.../demo/customer/CustomerReferenceResolver.java`
- Modify: `beanquery-demo/src/main/resources/data.sql`
- Modify: `beanquery-demo/src/main/resources/application.yml` (add `beanquery.max-reference-filter-ids: 1000` for visibility)
- Modify: `beanquery-demo/src/test/java/.../demo/DemoApplicationIT.java`, `DemoOrFilterTest.java` (package stays `demo`, no code change expected — verify)

**Interfaces:**
- `CustomerOrder`: `@Queryable(name = "order")`, `@Table(name = "customer_order")`, fields `Long id`, `String status`, `BigDecimal total`, `Long customerId` (`@QueryableField` + `@QueryableReference(name="customer", fields={"name","tier"})`).
- `CustomerReferenceResolver`: `@Component` in `demo.customer`, `referenceName() == "customer"`, reads the `customer` table via `JdbcTemplate`.

- [ ] **Step 1: Add demo dependencies** — `beanquery-demo/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-core</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Move `Product` and `Category`**

```bash
mkdir -p beanquery-demo/src/main/java/io/github/mszajner/beanquery/demo/product
git mv beanquery-demo/src/main/java/io/github/mszajner/beanquery/demo/Product.java \
       beanquery-demo/src/main/java/io/github/mszajner/beanquery/demo/product/Product.java
git mv beanquery-demo/src/main/java/io/github/mszajner/beanquery/demo/Category.java \
       beanquery-demo/src/main/java/io/github/mszajner/beanquery/demo/product/Category.java
```

Change both files' `package` to `io.github.mszajner.beanquery.demo.product`.

- [ ] **Step 3: Create `demo/customer/Customer.java`**

```java
package io.github.mszajner.beanquery.demo.customer;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Owned by the {@code customer} module. No other module references this class. */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    private Long id;
    private String name;
    private String tier;

    protected Customer() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTier() {
        return tier;
    }
}
```

- [ ] **Step 4: Create `demo/customer/CustomerReferenceResolver.java`**

```java
package io.github.mszajner.beanquery.demo.customer;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Serves {@code order.customer.*} from the {@code customer} table. The {@code order}
 * module never sees this class or {@link Customer}.
 */
@Component
public class CustomerReferenceResolver implements ReferenceResolver {

    private final JdbcTemplate jdbc;

    public CustomerReferenceResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String referenceName() {
        return "customer";
    }

    @Override
    public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) {
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        Map<Object, Map<String, Object>> out = new HashMap<>();
        jdbc.query("SELECT id, name, tier FROM customer WHERE id IN (" + placeholders + ")",
                ids.toArray(),
                rs -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("tier", rs.getString("tier"));
                    out.put(rs.getLong("id"), row);
                });
        return out;
    }

    @Override
    public Optional<Set<Object>> resolveFilter(String field, FilterOperator op, Object value) {
        String column = switch (field) {
            case "name" -> "name";
            case "tier" -> "tier";
            default -> null;
        };
        if (column == null) {
            return Optional.empty();
        }
        List<Long> ids = switch (op) {
            case EQ -> jdbc.queryForList("SELECT id FROM customer WHERE " + column + " = ?", Long.class, value);
            case ILIKE -> jdbc.queryForList(
                    "SELECT id FROM customer WHERE LOWER(" + column + ") LIKE ?",
                    Long.class, "%" + ((String) value).toLowerCase(Locale.ROOT) + "%");
            default -> null;
        };
        return ids == null ? Optional.empty() : Optional.of(new HashSet<>(ids));
    }
}
```

- [ ] **Step 5: Create `demo/order/CustomerOrder.java`**

```java
package io.github.mszajner.beanquery.demo.order;

import io.github.mszajner.beanquery.core.annotation.Queryable;
import io.github.mszajner.beanquery.core.annotation.QueryableField;
import io.github.mszajner.beanquery.core.annotation.QueryableReference;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Owned by the {@code order} module. Holds only {@code customerId} — the customer's
 * name/tier come from the {@code customer} module's {@code ReferenceResolver}.
 */
@Entity
@Table(name = "customer_order")
@Queryable(name = "order")
public class CustomerOrder {

    @Id
    @QueryableField
    private Long id;

    @QueryableField
    private String status;

    @QueryableField
    private BigDecimal total;

    @QueryableField
    @QueryableReference(name = "customer", fields = {"name", "tier"},
            operators = {FilterOperator.EQ, FilterOperator.ILIKE})
    private Long customerId;

    protected CustomerOrder() {
    }

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Long getCustomerId() {
        return customerId;
    }
}
```

- [ ] **Step 6: Seed `data.sql`** — append:

```sql
-- customer module -----------------------------------------------------------
INSERT INTO customer (id, name, tier) VALUES
  (1, 'Acme Corp',      'gold'),
  (2, 'Beta Industries','silver'),
  (3, 'Ceres Ltd',      'gold'),
  (4, 'Delta LLC',      'bronze');

-- order module (customer_id is a plain column, no FK constraint) -------------
INSERT INTO customer_order (id, status, total, customer_id) VALUES
  ( 1, 'NEW',     120.00, 1),
  ( 2, 'PAID',    340.00, 1),
  ( 3, 'NEW',      55.00, 2),
  ( 4, 'SHIPPED', 900.00, 3),
  ( 5, 'PAID',     72.50, 3),
  ( 6, 'NEW',     210.00, 4),
  ( 7, 'CANCELLED',18.00, 2),
  ( 8, 'NEW',     460.00, NULL),
  ( 9, 'PAID',    130.00, NULL),
  (10, 'SHIPPED', 999.00, 99);
```

- [ ] **Step 7: Run the demo build (compile + existing tests)**

Run: `./mvnw -q -pl beanquery-demo test`
Expected: PASS — `DemoApplicationIT` / `DemoOrFilterTest` still green (they hit `/api/bq/product/...`, unaffected by the package move because `@SpringBootApplication` scans sub-packages).

- [ ] **Step 8: License + commit**

```bash
./mvnw -q license:format
git add beanquery-demo pom.xml
git commit -m "feat(demo): split into product/order/customer modules with a ReferenceResolver"
```

---

## Task 16: Demo — modularity test + demo.http / demo.curl.sh

**Files:**
- Create: `beanquery-demo/src/test/java/io/github/mszajner/beanquery/demo/DemoModularityTest.java`
- Create: `beanquery-demo/src/test/java/io/github/mszajner/beanquery/demo/ReferenceDemoIT.java`
- Modify: `beanquery-demo/demo.http`
- Modify: `beanquery-demo/demo.curl.sh`

- [ ] **Step 1: Write `DemoModularityTest`**

```java
package io.github.mszajner.beanquery.demo;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** The {@code order} module must not depend on the {@code customer} module. */
class DemoModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(DemoApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        MODULES.verify();
    }
}
```

- [ ] **Step 2: Run it**

Run: `./mvnw -q -pl beanquery-demo test -Dtest=DemoModularityTest`
Expected: PASS. If it fails complaining `product`/`order`/`customer` aren't recognised as modules, add a `package-info.java` per module:

```java
@org.springframework.modulith.ApplicationModule
package io.github.mszajner.beanquery.demo.order;
```

If it fails because `order` genuinely depends on `customer` — fix the code (it must not). If it fails for a Boot-4-compat reason (stack trace inside Modulith internals, not a stated module violation) — **STOP, invoke the Task 14 contingency**.

- [ ] **Step 3: Write `ReferenceDemoIT`** — pins the new demo.http examples to row counts (mirror `DemoOrFilterTest`):

```java
package io.github.mszajner.beanquery.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReferenceDemoIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void selectWithCustomerName() throws Exception {
        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id","status","customer.name","customer.tier"],
                         "sort":[{"field":"id","direction":"ASC"}],"page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.id == 1)].['customer.name']").value(
                        org.hamcrest.Matchers.contains("Acme Corp")))
                .andExpect(jsonPath("$.rows[?(@.id == 8)].['customer.name']").value(
                        org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void filterOnCustomerNameIlike() throws Exception {
        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"filters":{"field":"customer.name","op":"ILIKE","value":"acme"},
                         "page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void filterResolvingToNothingIsZero() throws Exception {
        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"filters":{"field":"customer.name","op":"ILIKE","value":"nobody"},
                         "page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void sortOnCustomerNameIsRejected() throws Exception {
        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"sort":[{"field":"customer.name","direction":"ASC"}],
                         "page":{"number":0,"size":10}}"""))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 4: Add the `demo.http` section** — append:

```
########################################################################
### Referencje między modułami (order.customer.* via ReferenceResolver) #
### Moduł "order" trzyma tylko customer_id; nazwę/tier dostarcza moduł  #
### "customer" (tabela customer, czytana JdbcTemplate).                 #
########################################################################

### Select z polami referencyjnymi — jedno batchowe resolve() na stronę
POST {{bq}}/order/query
Content-Type: application/json

{
  "select": ["id", "status", "total", "customer.name", "customer.tier"],
  "sort": [{ "field": "id", "direction": "ASC" }],
  "page": { "number": 0, "size": 50 }
}

### Filtr po polu referencyjnym -> resolveFilter() -> customer_id IN (...)
POST {{bq}}/order/query
Content-Type: application/json

{
  "select": ["id", "customer.name"],
  "filters": { "field": "customer.name", "op": "ILIKE", "value": "acme" },
  "page": { "number": 0, "size": 50 }
}

### Filtr, który nie pasuje do nikogo -> 0 wierszy, totalElements 0 (nie wszystkie!)
POST {{bq}}/order/query
Content-Type: application/json

{
  "select": ["id"],
  "filters": { "field": "customer.name", "op": "ILIKE", "value": "nobody" },
  "page": { "number": 0, "size": 50 }
}

### Filtr referencyjny w gałęzi OR — IN wstawiony tylko tam
POST {{bq}}/order/query
Content-Type: application/json

{
  "select": ["id", "status", "customer.name"],
  "filters": { "logic": "or", "children": [
    { "field": "customer.name", "op": "ILIKE", "value": "acme" },
    { "field": "status", "op": "EQ", "value": "NEW" }
  ] },
  "sort": [{ "field": "id", "direction": "ASC" }],
  "page": { "number": 0, "size": 50 }
}

### 400 - sortowanie po polu referencyjnym jest niedozwolone
POST {{bq}}/order/query
Content-Type: application/json

{
  "select": ["id"],
  "sort": [{ "field": "customer.name", "direction": "ASC" }],
  "page": { "number": 0, "size": 10 }
}
```

- [ ] **Step 5: Mirror the section in `demo.curl.sh`** — add the same five requests as `curl | jq` blocks following the file's existing style.

- [ ] **Step 6: Run the demo module**

Run: `./mvnw -q -pl beanquery-demo test`
Expected: PASS (all four test classes).

- [ ] **Step 7: License + commit**

```bash
./mvnw -q license:format
git add beanquery-demo
git commit -m "test(demo): module boundary verification + reference query examples"
```

---

## Task 17: README + CHANGELOG

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Add the README section** — after `## Security / multi-tenancy`, before `## Version 1 limitations`, add `## Spring Modulith / module boundaries` containing:

  1. **When to use which** — a short paragraph: `JOINED` (`@QueryableField(nested=…)`) needs a JPA `@ManyToOne`/`@OneToOne`, so both entities live in one module; `REFERENCE` (`@QueryableReference`) needs only an id column and a `ReferenceResolver` bean, so the target entity can live in another module (or another service later).

  2. **`@QueryableReference` example** — the `CustomerOrder` snippet from Task 15 Step 5 plus a minimal `CustomerReferenceResolver` (Task 15 Step 4, trimmed), and one request/response showing `customer.name` in the rows.

  3. **The `VIEW` alternative** — full worked example:

     ```sql
     CREATE VIEW order_with_customer AS
     SELECT o.id, o.status, o.total, o.customer_id,
            c.name AS customer_name, c.tier AS customer_tier
     FROM   customer_order o
     LEFT JOIN customer c ON c.id = o.customer_id;
     ```

     ```java
     @Entity
     @Immutable
     @Subselect("SELECT * FROM order_with_customer")
     @Queryable(name = "orderView")
     public class OrderView {
         @Id @QueryableField Long id;
         @QueryableField String status;
         @QueryableField BigDecimal total;
         @QueryableField String customerName;
         @QueryableField String customerTier;
     }
     ```

     Trade-off note: full SQL-side **filtering and sorting** on `customerName` / `customerTier`, paid for with a database view that couples the two tables' schemas and a migration to maintain. Best when the modules share a database and are not about to be split.

  4. **Comparison table**:

     | | `JOINED` | `REFERENCE` | `VIEW` |
     |---|---|---|---|
     | Filtering | SQL | resolver → `id IN (…)` | SQL |
     | Sorting | SQL | not supported | SQL |
     | Crosses a module boundary | no | yes | no (shared schema) |
     | Ready to extract into a service | no | yes | no |
     | Extra moving parts | none | a resolver bean | a DB view + migration |

  5. **The limit** — `beanquery.max-reference-filter-ids` (default 1000): a reference filter that resolves to more ids than this is a `400`. It exists because the translated `id IN (…)` list would otherwise be unbounded and can hit database bind-parameter limits (PostgreSQL caps at 65535).

  6. **Why reference fields can't be sorted** — one sentence: sorting would only order the current page after enrichment, not the whole result; correct ordering would need every candidate id fetched and sorted at the resolver, which is out of scope for v1.

Also update `## Configuration` table with the new `beanquery.max-reference-filter-ids` row, and the `## Version 1 limitations` list with "Reference fields (`@QueryableReference`) are not sortable and support only depth-1 sub-fields."

- [ ] **Step 2: Add the CHANGELOG bullet** — under `## [Unreleased]` → `### Added`:

```markdown
- `@QueryableReference` + the `ReferenceResolver` SPI: expose fields of another
  Spring Modulith module's entity via a runtime resolver instead of a JPA
  association. Reference fields are selectable and filterable (the resolver
  translates a filter to a local `id IN (…)`), never sortable. New property
  `beanquery.max-reference-filter-ids` (default 1000).
```

- [ ] **Step 3: Verify links / build docs sanity**

Run: `./mvnw -q -pl beanquery-demo test` (unchanged, sanity) and eyeball the README section renders.

- [ ] **Step 4: Commit**

```bash
git add README.md CHANGELOG.md
git commit -m "docs: module boundaries section (JOINED vs REFERENCE vs VIEW)"
```

---

## Task 18: Full verification

**Files:** none — verification only.

- [ ] **Step 1: Clean build, both JDKs if available**

Run: `./mvnw clean verify`
Expected: `BUILD SUCCESS`, every module, including `DemoModularityTest` (`ApplicationModules.verify()`) and all `*IT`.

- [ ] **Step 2: License check**

Run: `./mvnw -q license:check`
Expected: `License check: N file(s) checked (OK: N, Missing: 0, Unknown: 0)`.

- [ ] **Step 3: Confirm the public API delta matches the spec** — `git diff main --stat` and check §12 of the spec: `QueryableReference`, `FieldKind`, `ReferenceMetadata`, `reference.ReferenceResolver`, `reference.ReferenceResolvers`, `ResolvedFilterNode.AlwaysFalse`, `FieldMetadata.kind`, `EntityMetadata.references`, `DynamicQueryExecutor` 4-arg ctor, `FieldDescriptor.kind`. Nothing else on the public surface.

- [ ] **Step 4: Final commit if any license reformat**

```bash
git add -A
git commit -m "chore: license headers" --allow-empty
```

- [ ] **Step 5: Open a PR** (do not push to `main` directly)

```bash
git push -u origin <branch>
gh pr create --fill --base main
```

---

## Self-Review

**1. Spec coverage**

| Spec section | Task |
|---|---|
| §1 annotation | 3, 4 |
| §2 `FieldKind` / `FieldMetadata` | 1 |
| §2 `ReferenceMetadata` / `EntityMetadata` | 2 |
| §2 `/metadata` `kind` | 10 |
| §3 SPI + `ReferenceResolvers` | 3; registry cross-check 5; starter wiring 12 |
| §4a filter translation | 7; wired 9 |
| §4b `AlwaysFalse` | 6 |
| §4c enrichment | 8; wired 9 |
| §4d selection bookkeeping | 9 |
| §5 sorting rejected | 1 (`sortable=false`) + 9 (executor guard) + 13/16 (tests) |
| §6 `max-reference-filter-ids` | 12; used in 7 |
| §7 authorizer hidden refs | 2 (`visibleMetadata`) |
| §7 authorizer mandatory ref filter | 11 |
| §8 demo restructure | 15 |
| §8 demo.http / curl | 16 |
| §9 README + CHANGELOG | 17 |
| §10 unit tests | 4, 5, 6, 7, 8, 11 |
| §10 integration test | 13 |
| §10 demo arch test | 16 |
| §11 Modulith dep + contingency | 14 (+ contingency notes in 14, 16) |
| §12 public API surface | 18 Step 3 |

No gaps.

**2. Placeholder scan** — all code steps carry real code. Task 7 Step 1 had a `.resolvers()` helper referenced in a test; the impl note replaces it with an inline `ReferenceResolvers` — the executor of Task 7 must apply that note. Task 6 Step 1 offers two test options and picks one explicitly. No "TODO"/"TBD".

**3. Type consistency**

- `ReferenceResolvers`: `find`, `require`, `names` — used consistently in Tasks 3, 7, 8, 9, 12.
- `ReferenceMetadata(name, idFieldPath, fields)` — same order in Tasks 2, 4, 7, 8, 9, 11.
- `FieldMetadata(name, path, javaType, selectable, filterable, sortable, allowedOperators, kind)` — 8-arg form used in every task after Task 1.
- `EntityMetadata(name, entityClass, fields, references)` — 4-arg form used everywhere after Task 2.
- `ReferenceEnricher.Selection(reference, subFields, idPerRow)` — Tasks 8 and 9 agree.
- `ReferenceFilterTranslator(resolvers, maxReferenceFilterIds)` + `translate(tree, meta)` — Tasks 7 and 9 agree.
- `DynamicQueryExecutor` 4-arg ctor `(EntityManager, FilterValueConverter, ReferenceResolvers, int)` — Tasks 9, 12, 13 agree.
- `QueryableEntityRegistry(EntityManagerFactory, Set<String>)` — Tasks 4, 5, 12 agree (starter passes `resolvers.names()`).

Consistent.
