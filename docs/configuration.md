---
title: Configuration and extension
permalink: /configuration/
---

## `beanquery.*` properties

| Property | Default | Description |
|---|---|---|
| `beanquery.enabled` | `true` | `false` switches the whole API off |
| `beanquery.base-path` | `/api/bq` | base path of both endpoints |
| `beanquery.max-page-size` | `200` | largest allowed `page.size`; larger is a `400` |
| `beanquery.default-page-size` | `20` | page size used when a request omits `page` |
| `beanquery.max-filter-depth` | `5` | maximum nesting depth of `AND`/`OR` groups |
| `beanquery.max-filter-conditions` | `50` | maximum number of conditions per request |
| `beanquery.max-reference-filter-ids` | `1000` | maximum id set returned by `ReferenceResolver.resolveFilter` |

```yaml
beanquery:
  base-path: /internal/query
  max-page-size: 100
  default-page-size: 25
  max-filter-conditions: 30
```

Values are validated at startup (`max-page-size`, `max-filter-depth`, `max-filter-conditions` must be >= 1).

## When the starter activates

`BeanQueryAutoConfiguration` activates when:

- `jakarta.persistence.EntityManager` is on the classpath,
- an `EntityManagerFactory` bean exists,
- `beanquery.enabled` is not `false`.

It runs after `HibernateJpaAutoConfiguration`.

## Beans and replacing them

Every bean is `@ConditionalOnMissingBean` - declare your own and the starter will not override it.

| Bean | Role | Why replace it |
|---|---|---|
| `QueryableEntityRegistry` | scans `@Queryable`, holds the metadata | usually not at all |
| `FilterValueConverter` | JSON -> field type | custom value formats, extra types |
| `QueryRequestValidator` | request validation rules | extra business rules |
| `DynamicQueryExecutor` | building and running the Criteria query | changing the query strategy |
| `QueryAuthorizationService` | aggregates authorizers | rarely |
| `ReferenceResolvers` | resolver registry | rarely |
| `BeanQueryController` | REST endpoints | custom mapping / extra logic |
| `BeanQueryExceptionHandler` | error mapping | a different error format |

You do not need to replace anything to add authorization or references: just register
`QueryAuthorizer` ([Security]({{ '/security/' | relative_url }})) and `ReferenceResolver`
([References]({{ '/references/' | relative_url }})) beans - the starter picks them up.

### Example: a custom value converter

The converter delegates to Spring's `ConversionService`, so the simplest approach is to add a conversion to it:

```java
@Bean
FilterValueConverter beanQueryFilterValueConverter() {
    DefaultConversionService cs = new DefaultConversionService();
    cs.addConverter(String.class, Money.class, Money::parse);
    return new FilterValueConverter(cs);
}
```

Remember that for a type outside the default list (e.g. `Money`) you must also set `operators` in `@QueryableField`.

### Example: custom validation rules

```java
@Bean
QueryRequestValidator beanQueryRequestValidator(BeanQueryProperties p) {
    return new QueryRequestValidator(p.getMaxPageSize(), p.getMaxFilterDepth(), p.getMaxFilterConditions()) {
        @Override
        public void validate(QueryRequest request, EntityMetadata metadata) {
            super.validate(request, metadata);
            if (request.select().size() > 20) {
                throw new InvalidQueryException(List.of("select: at most 20 fields"));
            }
        }
    };
}
```

> The library is pre-1.0 - the API of the engine classes (`DynamicQueryExecutor`, `QueryRequestValidator`) may still change.
> The stable contract is the annotations, `QueryAuthorizer`, `ReferenceResolver` and the shape of the REST requests/responses.

## Without the starter (`beanquery-core`)

If you use `beanquery-core` alone, declare the beans by hand (follow `BeanQueryAutoConfiguration`) -
notably `QueryableEntityRegistry`, `FilterValueConverter`, `QueryRequestValidator`, `DynamicQueryExecutor`,
`QueryAuthorizationService`, `BeanQueryController` and `BeanQueryExceptionHandler`.

## Spring Modulith

beanquery does not require Spring Modulith but plays well with its module boundaries: an entity with
`@QueryableReference` does not depend on classes of the other module, and the `ReferenceResolver` lives in the module
that owns the data. The demo module includes a test that verifies the boundaries (`DemoModularityTest`).
