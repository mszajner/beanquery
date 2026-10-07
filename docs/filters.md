---
title: Filters and operators
permalink: /filters/
---

## Filter syntax

`filters` accepts **two shapes** - a tree and the older flat array.

### `AND`/`OR` tree

A node with a `field` key is a condition (leaf), a node with a `logic` key is a group. There is no `type` field -
the shape is recognised by the keys.

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

That is: `status = NEW AND (totalAmount > 1000 OR customer.name contains "acme")`.

- `logic` is case-insensitive (`"and"` / `"AND"`).
- A group must have a non-empty `children` list.
- A single condition can be the root: `"filters": { "field": "x", "op": "EQ", "value": 1 }`.
- An association used in several branches is joined once.

### Flat array (backward compatibility)

```json
"filters": [
  { "field": "status", "op": "EQ", "value": "ACTIVE" },
  { "field": "price",  "op": "GT", "value": 100 }
]
```

It is treated as `{ "logic": "AND", "children": [ ... ] }`. An empty array, `null` or a missing `filters` mean "no filter".

### Limits

Exceeding either is a `400` (never a `500`):

| Limit | Property | Default |
|---|---|---|
| nesting depth of groups | `beanquery.max-filter-depth` | 5 |
| total number of conditions (leaves) | `beanquery.max-filter-conditions` | 50 |

The current values are returned by `/metadata` in `capabilities`.

## Operators

| Operator | Meaning | `value` shape |
|---|---|---|
| `EQ`, `NE` | equal / not equal | scalar |
| `GT`, `GTE`, `LT`, `LTE` | comparison | scalar |
| `BETWEEN` | inclusive range | 2-element array `[from, to]` |
| `IN`, `NOT_IN` | member / not member | non-empty array |
| `LIKE` | *contains*, **case-sensitive** | scalar (string) |
| `ILIKE` | *contains*, case-insensitive | scalar (string) |
| `IS_NULL`, `IS_NOT_NULL` | null test | omit `value` (supplying one is an error) |

`LIKE`/`ILIKE` always mean **"contains"** (`%value%`), and `%`, `_` and `\` in the value are matched
literally - a client cannot inject its own wildcards. There is no "starts with" or regex matching.

Arrays in `IN`/`NOT_IN`/`BETWEEN` must not contain `null`.

### Default operators {#default-operators}

When `@QueryableField(operators = {})` is empty:

| Type | Operators |
|---|---|
| `String` | `EQ`, `NE`, `LIKE`, `ILIKE`, `IN`, `IS_NULL`, `IS_NOT_NULL` |
| numbers (incl. primitives), `BigDecimal`, `LocalDate`, `LocalDateTime`, `Instant` | `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `BETWEEN`, `IN`, `IS_NULL`, `IS_NOT_NULL` |
| `boolean` / `Boolean` | `EQ`, `NE`, `IS_NULL`, `IS_NOT_NULL` |
| `enum` | `EQ`, `NE`, `IN`, `NOT_IN`, `IS_NULL`, `IS_NOT_NULL` |
| others (e.g. `UUID`, `OffsetDateTime`) | *none* - specify `operators` explicitly |

An operator used must be in the field's `operators` set (read it from `/metadata`), otherwise `400`.

## Value conversion

The JSON value is converted to the field type on the server:

| Field type | Value format |
|---|---|
| `LocalDate` | ISO-8601: `"2026-09-09"` |
| `LocalDateTime` | ISO-8601: `"2026-09-09T10:15:30"` |
| `Instant` | ISO-8601 with zone: `"2026-09-09T10:15:30Z"` |
| `BigDecimal`, `BigInteger`, numbers | JSON number or string |
| `UUID` | string |
| enum | constant name, case-insensitive (`"active"` -> `ACTIVE`) |
| other | Spring `ConversionService` (`String -> type`) |

A value that cannot be converted gives a `400` with a message naming the field, the expected type and the received
value, e.g. `filter on 'releasedOn': expected LocalDate but got "yesterday"`. You can replace the converter - see
[Configuration and extension]({{ '/configuration/' | relative_url }}).

## Examples

**Price range and enum:**

```json
{ "logic": "and", "children": [
  { "field": "price",  "op": "BETWEEN", "value": [100, 500] },
  { "field": "status", "op": "IN",      "value": ["ACTIVE", "DRAFT"] }
]}
```

**Missing data:**

```json
{ "field": "category.id", "op": "IS_NULL" }
```

**Search across several columns (OR):**

```json
{ "logic": "or", "children": [
  { "field": "name", "op": "ILIKE", "value": "dock" },
  { "field": "sku",  "op": "ILIKE", "value": "dock" }
]}
```

> All filters from an authorizer (e.g. `tenantId`) are combined with `AND` **over the whole** user tree,
> so a client's outer `OR` can never bypass them - see [Security]({{ '/security/' | relative_url }}).
