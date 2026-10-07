---
title: API reference
permalink: /api/
---

The default base path is `/api/bq` (change it with `beanquery.base-path`).
`{entity}` is the name from `@Queryable(name = ...)`.

## `GET /{entity}/metadata`

Returns a description of the fields the caller may use right now (after authorization is applied).

```json
{
  "entity": "product",
  "fields": [
    { "name": "id",     "type": "number", "selectable": true, "filterable": true, "sortable": true,
      "operators": ["EQ","NE","GT","GTE","LT","LTE","BETWEEN","IN","IS_NULL","IS_NOT_NULL"], "kind": "COLUMN" },
    { "name": "status", "type": "enum", "values": ["DRAFT","ACTIVE","DISCONTINUED"],
      "selectable": true, "filterable": true, "sortable": true,
      "operators": ["EQ","NE","IN","NOT_IN","IS_NULL","IS_NOT_NULL"], "kind": "COLUMN" },
    { "name": "category.name", "type": "string", "selectable": true, "filterable": true, "sortable": true,
      "operators": ["EQ","NE","LIKE","ILIKE","IN","IS_NULL","IS_NOT_NULL"], "kind": "JOINED" }
  ],
  "capabilities": { "filterLogic": ["AND","OR"], "maxFilterDepth": 5, "maxFilterConditions": 50 }
}
```

| Field | Description |
|---|---|
| `name` | name used in `select` / `filters` / `sort` (for associations: a dotted path) |
| `type` | `string`, `number`, `date`, `datetime`, `boolean`, `enum` |
| `values` | `enum` only - the allowed values |
| `selectable` / `filterable` / `sortable` | what may be done with the field |
| `operators` | filter operators allowed for the field |
| `kind` | `COLUMN`, `JOINED` (SQL join) or `REFERENCE` (resolver, not sortable) |
| `capabilities` | limits of the filter tree for this deployment |

## `POST /{entity}/query`

### Request

```json
{
  "select":  ["id", "name", "price"],
  "filters": { "logic": "and", "children": [ { "field": "price", "op": "GT", "value": 100 } ] },
  "sort":    [ { "field": "price", "direction": "DESC" } ],
  "page":    { "number": 0, "size": 20 }
}
```

| Field | Required | Description |
|---|---|---|
| `select` | yes | non-empty list of fields to return; key order in the response = order here |
| `filters` | no | an `AND`/`OR` tree or a flat array of conditions (`AND`); missing/`null`/`[]` = no filter - [details]({{ '/filters/' | relative_url }}) |
| `sort` | no | list of `{ field, direction }`, `direction` = `ASC` / `DESC`; applied in order |
| `page` | no | `{ number, size }`, `number` >= 0 (zero-based), `size` from 1 to `max-page-size`; defaults to `0` and `default-page-size` |

An ascending sort on the primary key is always appended last, so the order is deterministic and pagination is stable.

### `200` response

```json
{
  "rows": [
    { "id": 10, "name": "USB-C Dock Pro", "price": 149.00 },
    { "id": 5,  "name": "Pixel Camera X", "price": 699.00 }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 2, "totalPages": 1 }
}
```

A row is a flat map: the key is exactly the field name from `select` (including `category.name` - not a nested object).
`totalElements` comes from a separate `COUNT` query with the same filters (including those forced by authorization).

## Status codes and errors

| Status | When | Body |
|---|---|---|
| `200` | success | see above |
| `400` | validation, unparseable filter value, malformed JSON, too large an id set from a resolver | `{ "errors": ["...", "..."] }` |
| `403` | a `QueryAuthorizer` denied the call | `{ "error": "access_denied" }` |
| `404` | unknown entity | `{ "error": "No queryable entity is registered under name 'x'" }` |
| `500` | unexpected error or a misconfigured authorizer | `{ "error": "Internal server error" }`, details only in the server log |

Validation errors are **collected all at once** and point to the path in the request:

```json
{
  "errors": [
    "select: unknown field 'secret'",
    "filters.children[1].children[0]: field 'x' is not filterable",
    "filters.children[0]: operator GT is not allowed for field 'status'",
    "sort: field 'category.name' is not sortable",
    "page.size: must be between 1 and 200"
  ]
}
```

Error handling applies only to the beanquery controller (a `@RestControllerAdvice` restricted to its class)
and does not affect the rest of your application.

## Calling from Java code

The REST layer is a thin wrapper. If you need the same queries inside the application (without HTTP),
inject `DynamicQueryExecutor`, `QueryableEntityRegistry` and `QueryRequestValidator`:

```java
EntityMetadata meta = registry.getRequired("product");
validator.validate(request, meta);              // throws InvalidQueryException
QueryResult result = executor.execute(meta, request);   // request.page() must be set
```

> This call **bypasses `QueryAuthorizer`** (authorization is part of the controller). If you use the executor
> directly, pass the forced predicates yourself or restrict access in your own layer.

> The `web` package is considered internal; the public API is `annotation`, `metadata`, `query`, `security`
> and `io.github.mszajner.beanquery.starter`.
