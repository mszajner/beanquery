---
title: Client integration
permalink: /client/
---

The `/metadata` endpoint lets a client (React, Angular, Vue, a mobile app...) **avoid hard-coding**
the list of fields, types and operators. The examples below show typical usage; the library ships no ready-made UI.

## TypeScript types

```ts
type FieldType = 'string' | 'number' | 'date' | 'datetime' | 'boolean' | 'enum';
type Operator = 'EQ'|'NE'|'GT'|'GTE'|'LT'|'LTE'|'BETWEEN'|'IN'|'NOT_IN'|'LIKE'|'ILIKE'|'IS_NULL'|'IS_NOT_NULL';

interface FieldDescriptor {
  name: string; type: FieldType; values?: string[];
  selectable: boolean; filterable: boolean; sortable: boolean;
  operators: Operator[]; kind: 'COLUMN' | 'JOINED' | 'REFERENCE';
}
interface Metadata {
  entity: string; fields: FieldDescriptor[];
  capabilities: { filterLogic: ('AND'|'OR')[]; maxFilterDepth: number; maxFilterConditions: number };
}

type FilterNode =
  | { field: string; op: Operator; value?: unknown }
  | { logic: 'and' | 'or'; children: FilterNode[] };

interface QueryRequest {
  select: string[]; filters?: FilterNode;
  sort?: { field: string; direction: 'ASC' | 'DESC' }[];
  page?: { number: number; size: number };
}
interface QueryResponse {
  rows: Record<string, unknown>[];
  page: { number: number; size: number; totalElements: number; totalPages: number };
}
```

## Fetching metadata and querying

```ts
const base = '/api/bq';

export async function metadata(entity: string): Promise<Metadata> {
  const r = await fetch(`${base}/${entity}/metadata`);
  if (!r.ok) throw new Error(`metadata: HTTP ${r.status}`);
  return r.json();
}

export async function query(entity: string, req: QueryRequest): Promise<QueryResponse> {
  const r = await fetch(`${base}/${entity}/query`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (r.status === 400) throw { validation: (await r.json()).errors as string[] };
  if (!r.ok) throw new Error(`query: HTTP ${r.status}`);
  return r.json();
}
```

## Building a UI from the metadata

| UI element | Source in `/metadata` |
|---|---|
| Column picker | fields with `selectable: true` |
| Field list in the filter builder | fields with `filterable: true` |
| Operator list after choosing a field | that field's `operators` |
| Value control | `type`: `date` -> date picker, `enum` -> select from `values`, `boolean` -> checkbox, `number` -> numeric input |
| Value for `BETWEEN` / `IN` | two inputs / a list; for `IS_NULL` / `IS_NOT_NULL` - no value input |
| Sortable header | `sortable: true` (`REFERENCE` fields are never sortable) |
| Builder limits (depth, condition count) | `capabilities` |

Tips:

- Metadata depends on the caller (an authorizer may hide fields) - do not cache it globally across users.
- Show the `errors` from a `400` to the user or in a developer console - they give the exact path (`filters.children[1]...`).
- Page numbers are zero-based; the page count is `page.totalPages`.
- Row keys are dotted field names (`row["category.name"]`), not nested objects.
- Do not send `value` for `IS_NULL` / `IS_NOT_NULL` - the server returns `400`.

## CORS and authentication

beanquery configures neither CORS nor authentication - it is a plain Spring MVC controller, so your Spring Security /
`WebMvcConfigurer` configuration applies. Remember to cover the `beanquery.base-path` path with access rules and
CSRF like any other `POST` (the query is a `POST`, although it does not modify data).
