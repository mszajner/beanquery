---
title: Security and multi-tenancy
---

## The security model built into the library

- **Whitelist.** Every field in `select`, `filters` and `sort` must be registered via `@QueryableField`. Anything else is a `400`.
  Request field names are resolved against the metadata, and only the registered property path reaches the Criteria API -
  they are **never concatenated into JPQL or SQL**.
- **Per-capability flags.** `selectable` / `filterable` / `sortable` are enforced independently.
- **Operator allow-list** per field. `LIKE`/`ILIKE` always produce a "contains" match with `%` and `_` escaped.
- **Bounded result size.** `page.size` <= `beanquery.max-page-size` (200); every query is paginated.
- **Bounded filter complexity.** Depth and condition limits guard against expensive queries.
- **Read-only.** Queries run in a `readOnly` transaction; there is no write path.
- **Errors don't leak.** Validation -> `400` with a list, denial -> `403` without details, anything else -> a generic `500`
  (details only in the log).

## What the library does *not* do

beanquery **does not know Spring Security or your permission model** and is not handed the user's identity.

> **With no `QueryAuthorizer` registered, every caller can query every row of every `@Queryable` entity.**
> In a multi-tenant application this hook is mandatory - a missing `tenantId` predicate is a data leak.
{: .warn}

It is up to you to:

1. secure the `/api/bq/**` path (authentication, roles) with standard Spring Security tools,
2. restrict **rows** and **fields** per user via a `QueryAuthorizer`,
3. choose deliberately what to annotate with `@QueryableField` - an unannotated field is unreachable.

## `QueryAuthorizer`

Register one or more beans implementing the interface. They are called on **every** `/metadata`
and `/query` call, before the request is validated.

```java
public interface QueryAuthorizer {
    boolean supports(EntityMetadata meta);                       // do I have an opinion on this entity
    QueryAuthorization authorize(QueryAuthorizationContext ctx); // ctx.meta(), ctx.request() (null for /metadata)
}
```

The returned `QueryAuthorization` can:

| Action | Effect |
|---|---|
| `deny()` | `403` `{ "error": "access_denied" }` |
| `mandatoryFilter(field, op, value)` | a predicate `AND`-ed **over the whole** client filter tree - also in the `COUNT` query |
| `hideField(name)` | the field behaves as if it did not exist: using it is a `400 unknown field`, and it disappears from `/metadata` |

An `authorize` that returns `null` is treated like `deny()`. Hiding a field that does not exist is ignored (with a log warning).

With several authorizers: ordered by `@Order`, forced filters and hidden fields are **summed**, and any `DENY` wins.

A `MandatoryFilter` value **must already be of the field's type** (the library does not convert it), the field must be
registered, and the operator allowed for the field. A violation is a host configuration error: a `500` with a description
in the log (entity, field, authorizer class) - the query is never silently run without the filter.

### Example 1 - tenant isolation

```java
@Component
public class TenantAuthorizer implements QueryAuthorizer {

    @Override
    public boolean supports(EntityMetadata meta) {
        return meta.field("tenantId").isPresent();       // only entities that carry a tenant column
    }

    @Override
    public QueryAuthorization authorize(QueryAuthorizationContext ctx) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUser user)) {
            return QueryAuthorization.deny();
        }
        return QueryAuthorization.builder()
                .mandatoryFilter("tenantId", FilterOperator.EQ, user.tenantId())  // value already typed
                .hideField("tenantId")                                            // the client never sees the field
                .allow();
    }
}
```

Every query runs `... AND tenant_id = ?` for both the rows and the `COUNT`; the client cannot escape the tenant
even by sending a top-level `OR`.

> If you hide the `tenantId` field from the client, it can still serve as a `MandatoryFilter` - the filter is resolved
> against the entity's full metadata.

### Example 2 - hide a field from non-admins

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

A non-admin using `purchasePrice` in `select`/`filters`/`sort` gets `400 "unknown field 'purchasePrice'"`,
and `GET /metadata` does not show it at all - no hint that the field exists.

### Good practice

- Treat a missing security context as `deny()`, not `allow()`.
- For tenant data, start with an integration test that tries to escape the tenant through `OR`.
- Do not annotate fields nobody should see - that is the strongest protection.
- Note that `totalElements` and `/metadata` are information too - forced filters cover `COUNT`, and hidden fields vanish from metadata.
- Consider rate limiting and query timeouts at the infrastructure level - the library limits complexity, not the number of calls.
