---
title: References and module boundaries
---

Two `@Queryable` entities can be connected in three ways. In practice the choice is about
**where the module boundary sits**.

| | `JOINED` | `REFERENCE` | SQL view (`VIEW`) |
|---|---|---|---|
| Filtering | SQL | resolver -> `id IN (...)` | SQL |
| Sorting | SQL | **no** | SQL |
| Crosses a module boundary | no | **yes** | no (shared schema) |
| Ready to extract into a service | no | **yes** | no |
| Extra moving parts | none | a `ReferenceResolver` bean | a DB view + migration |

## `JOINED` - a JPA association

`@QueryableField(nested = {...})` on a `@ManyToOne` / `@OneToOne`. The database performs the join, so filtering
**and sorting** work natively. It needs a real JPA association - both entities must be in the same
persistence unit and, in practice, in the same module. See [Entity mapping]({{ '/entities/' | relative_url }}).

## `REFERENCE` - fields from another module

`@QueryableReference` goes on a plain foreign-key column (no association, no join). The module that owns the entity
knows only the `id`; the values of the target fields are supplied at runtime by a `ReferenceResolver` bean.
The target entity can live in another Spring Modulith module - or later in another service -
and the owning module never sees its class.

### Step 1: the entity with a reference

```java
@Entity
@Table(name = "customer_order")
@Queryable(name = "order")
public class CustomerOrder {

    @Id @QueryableField
    private Long id;

    @QueryableField
    private String status;

    @QueryableField
    private BigDecimal total;

    @QueryableField
    @QueryableReference(name = "customer", fields = {"name", "tier"},
            operators = {FilterOperator.EQ, FilterOperator.ILIKE})
    private Long customerId;
}
```

This exposes `customer.name` and `customer.tier` as **read-only, non-sortable** fields (`kind: REFERENCE`).
`customerId` itself remains a normal field that can be filtered and sorted.

| `@QueryableReference` attribute | Meaning |
|---|---|
| `name` | field-name prefix in the API and the resolver match key; non-blank, no dot |
| `fields` | short sub-field names (non-blank, no dots) |
| `operators` | operators advertised for the sub-fields; empty = `EQ, NE, IN, NOT_IN, LIKE, ILIKE` (`IS_NULL`/`IS_NOT_NULL` are always allowed and evaluated on the local id column) |

The annotation also requires `@QueryableField` on the same field and must sit on a scalar foreign-key column.

### Step 2: the resolver in the other module

One bean per reference name. A missing resolver with a matching `referenceName()` fails at startup.

```java
@Component
public class CustomerReferenceResolver implements ReferenceResolver {

    private final JdbcTemplate jdbc;

    public CustomerReferenceResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public String referenceName() { return "customer"; }

    // result enrichment: id -> (sub-field -> value); one call per page
    @Override
    public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) {
        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        Map<Object, Map<String, Object>> out = new HashMap<>();
        jdbc.query("SELECT id, name, tier FROM customer WHERE id IN (" + placeholders + ")",
                rs -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("tier", rs.getString("tier"));
                    out.put(rs.getLong("id"), row);
                },
                ids.toArray());
        return out;
    }

    // filter translation: a condition on a sub-field -> matching local ids
    @Override
    public Optional<Set<Object>> resolveFilter(String field, FilterOperator op, Object value) {
        String column = switch (field) { case "name" -> "name"; case "tier" -> "tier"; default -> null; };
        if (column == null) return Optional.empty();                 // unsupported -> 400
        List<Long> ids = switch (op) {
            case EQ -> jdbc.queryForList("SELECT id FROM customer WHERE " + column + " = ?", Long.class, value);
            case ILIKE -> jdbc.queryForList("SELECT id FROM customer WHERE LOWER(" + column + ") LIKE ?",
                    Long.class, "%" + ((String) value).toLowerCase(Locale.ROOT) + "%");
            default -> null;
        };
        return ids == null ? Optional.empty() : Optional.of(new HashSet<>(ids));
    }
}
```

> In `resolveFilter` the column name comes from a `switch` over a **known set** of names, never from client input - keep that pattern.
> Always pass the value as a parameter. The value reaches the resolver as a `String` (or a `List` of strings for `IN`/`NOT_IN`).
{: .warn}

### Step 3: query

```json
POST /api/bq/order/query
{
  "select":  ["id", "status", "total", "customer.name", "customer.tier"],
  "filters": { "field": "customer.name", "op": "ILIKE", "value": "acme" },
  "sort":    [ { "field": "id", "direction": "ASC" } ],
  "page":    { "number": 0, "size": 20 }
}
```

`resolveFilter` turns the condition into `customer_id IN (1)`, the query runs against `customer_order` alone,
and `resolve` fills the `customer.*` keys for the returned page:

```json
{
  "rows": [
    { "id": 1, "status": "NEW",  "total": 120.00, "customer.name": "Acme Corp", "customer.tier": "gold" },
    { "id": 2, "status": "PAID", "total": 340.00, "customer.name": "Acme Corp", "customer.tier": "gold" }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 2, "totalPages": 1 }
}
```

### Behaviour and pitfalls

- **No matches != no filter.** A reference filter that matches nothing yields an always-false predicate (0 rows).
- **Missing record** (deleted customer, `NULL` foreign key) - every sub-field is `null`.
- **Id set limit.** If `resolveFilter` returns more than `beanquery.max-reference-filter-ids` values (default `1000`),
  the request gets a `400` ("narrow it") - this protects against driver parameter limits (PostgreSQL: 65,535).
- **Why no sorting.** Sorting by `customer.name` would only reorder the current page *after* enrichment,
  not the whole result. Doing it right would require fetching and sorting every candidate at the resolver before paging -
  out of scope for version 1.
- **Depth 1.** Only flat sub-fields are supported (`customer.name`).
- Performance: `resolve` is called **once per page** (batch) - write it as a single `IN` query, not a loop.

## SQL view - when modules share a database

If the modules share a database and you do not intend to split them, a database view gives you filtering **and sorting**
on the borrowed columns without a resolver:

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

`customerName` and `customerTier` are ordinary scalar columns. The cost: the view couples the schemas of both tables and
needs a migration maintained alongside both modules.
