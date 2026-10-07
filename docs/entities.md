---
title: Entity mapping
permalink: /entities/
---

The whole "data model" of the API is annotations on your JPA entities. Package:
`io.github.mszajner.beanquery.core.annotation`.

## `@Queryable`

On the entity class. Includes the entity in the API.

| Attribute | Default | Meaning |
|---|---|---|
| `name` | class name in `lowerCamelCase` (`OrderLine` -> `orderLine`) | identifier in the URL: `/api/bq/{name}/query` |

Names must be unique across the application - a duplicate causes a `QueryableMetadataException` at startup.

## `@QueryableField`

On a field (`FIELD`). **Only fields with this annotation are visible** to the API.

| Attribute | Default | Meaning |
|---|---|---|
| `selectable` | `true` | the field may appear in `select` |
| `filterable` | `true` | the field may appear in `filters` |
| `sortable` | `true` | the field may appear in `sort` |
| `operators` | `{}` | allowed operators; empty = derived from the field type ([table]({{ '/filters/#default-operators' | relative_url }})) |
| `nested` | `{}` | **required** on `@ManyToOne`/`@OneToOne`, **forbidden** on scalar fields |

The flags work independently, e.g. a column can be returned but not filterable:

```java
@QueryableField(filterable = false, sortable = false)
private String description;

@QueryableField(operators = {FilterOperator.EQ, FilterOperator.IN})
private String sku;                 // equality and IN only
```

## Association fields (`JOINED`)

For `@ManyToOne` / `@OneToOne`, list the target entity properties you want to expose.
Each becomes a separate field with the path `field.property` and inherits the flags from the annotation:

```java
@ManyToOne
@QueryableField(nested = {"id", "name"}, sortable = false)
private Category category;
// -> "category.id", "category.name" (selectable, filterable, not sortable)
```

- The depth is exactly **1** (`customer.address.city` is rejected).
- Properties in `nested` must exist on the target class, and the field type is taken from that property
  (it decides the default operators).
- The target entity does **not** need its own `@Queryable`.
- The join is a `LEFT JOIN`, de-duplicated within a query (an association used in several filter branches is joined once).

## Fields from another module (`REFERENCE`)

The `@QueryableReference` annotation exposes fields of an entity whose class this module cannot see.
Described in [References and module boundaries]({{ '/references/' | relative_url }}).

## Supported field types

| Java type | Type in `/metadata` | Default operators |
|---|---|---|
| `String` | `string` | yes |
| `Integer`, `Long`, `Double`, ... and primitives, `BigDecimal`, `BigInteger` | `number` | yes |
| `boolean`, `Boolean` | `boolean` | yes |
| `LocalDate` | `date` | yes |
| `LocalDateTime`, `Instant` | `datetime` | yes |
| `enum` | `enum` + `values` | yes |
| `OffsetDateTime`, `ZonedDateTime` | `datetime` | **none** - set `operators` explicitly |
| `UUID` and other types | `string` | **none** - set `operators` explicitly |

> For types without default operators the field is **not filterable** until you provide
> `@QueryableField(operators = {...})`, e.g. `operators = {FilterOperator.EQ, FilterOperator.IN}` for `UUID`.
> Values are converted server-side (`UUID`, `BigDecimal`, ISO-8601 dates, enums by name;
> the rest via Spring's `ConversionService`). See [Filters and operators]({{ '/filters/' | relative_url }}).

Fields inherited from superclasses (e.g. `@MappedSuperclass`) are scanned the same way as the entity's own fields.

## Validation at startup

The registry builds the metadata after the singletons are created and throws `QueryableMetadataException` when, among others:

- two entities share a name, or two fields share a path name,
- a `@ManyToOne`/`@OneToOne` association has no `nested`, or `nested` is on a scalar field,
- a `nested` entry contains a dot or does not exist on the target class,
- `@QueryableReference` is on a field without `@QueryableField`, on an association/collection, or has a blank name or no fields,
- no `ReferenceResolver` bean with a matching `referenceName()` exists.

Mapping mistakes thus surface at startup rather than on the first request.

## What cannot be mapped

Collections (`@OneToMany`, `@ManyToMany`) and computed fields/aliases are not supported; embedded types
(`@Embedded`) have no dedicated support. See [Limitations]({{ '/limitations/' | relative_url }}).
The annotations work on fields (`FIELD`), not on getters.
