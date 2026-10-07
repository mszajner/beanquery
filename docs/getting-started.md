---
title: Quick start
permalink: /getting-started/
---

This guide takes you from an empty Spring Boot project to a working query.

## 1. Dependency

```xml
<dependency>
    <groupId>io.github.mszajner</groupId>
    <artifactId>beanquery-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'io.github.mszajner:beanquery-starter:0.1.0'
```

The starter switches itself on when the context has an `EntityManagerFactory` (JPA) and Spring MVC.
So you also need `spring-boot-starter-data-jpa`, the Spring Boot web starter and a database driver.

> `beanquery-core` contains the whole engine and REST layer but no auto-configuration.
> Use it only if you want to declare every bean yourself. See
> [Configuration and extension]({{ '/configuration/' | relative_url }}).

## 2. Annotate an entity

```java
import io.github.mszajner.beanquery.core.annotation.Queryable;
import io.github.mszajner.beanquery.core.annotation.QueryableField;

@Entity
@Queryable(name = "product")          // URL id; defaults to the decapitalised class name
public class Product {

    @Id
    @QueryableField
    private Long id;

    @QueryableField
    private String name;

    @QueryableField
    private BigDecimal price;

    @QueryableField
    @Enumerated(EnumType.STRING)
    private Status status;

    @ManyToOne
    @QueryableField(nested = {"id", "name"})   // -> fields "category.id", "category.name"
    private Category category;

    private String internalNote;               // invisible
}
```

The entity registry scans the JPA metamodel at application startup. A misconfiguration
(e.g. a duplicate name, an association without `nested`) fails **at startup**, not at runtime -
details in [Entity mapping]({{ '/entities/' | relative_url }}).

## 3. Start the app and fetch the metadata

```bash
curl -s localhost:8080/api/bq/product/metadata | jq
```

```json
{
  "entity": "product",
  "fields": [
    { "name": "price", "type": "number", "selectable": true, "filterable": true, "sortable": true,
      "operators": ["EQ","NE","GT","GTE","LT","LTE","BETWEEN","IN","IS_NULL","IS_NOT_NULL"],
      "kind": "COLUMN" }
  ],
  "capabilities": { "filterLogic": ["AND","OR"], "maxFilterDepth": 5, "maxFilterConditions": 50 }
}
```

## 4. Send a query

```bash
curl -s -X POST localhost:8080/api/bq/product/query \
  -H 'Content-Type: application/json' \
  -d '{
        "select": ["id", "name", "price"],
        "filters": [ { "field": "price", "op": "GT", "value": 100 } ],
        "sort": [ { "field": "price", "direction": "DESC" } ],
        "page": { "number": 0, "size": 10 }
      }' | jq
```

Omitting `page` means page `0` of size `beanquery.default-page-size` (20).

## 5. Secure the API

> **Important:** without a registered `QueryAuthorizer`, **anyone who can reach the endpoint can read all rows of
> every `@Queryable` entity**. beanquery knows nothing about your Spring Security setup - secure the
> `/api/bq/**` path as usual (authentication filter) and add an authorizer for per-tenant/per-role data.
{: .warn}

See [Security and multi-tenancy]({{ '/security/' | relative_url }}).

## Demo application

The repository contains a `beanquery-demo` module (in-memory H2, 55 rows, `product`, `category` and `order`
entities with a customer reference):

```bash
git clone https://github.com/mszajner/beanquery.git
cd beanquery
./mvnw -DskipTests install
./mvnw -pl beanquery-demo spring-boot:run
```

Ready-made `AND`/`OR` examples are in
[`demo.http`]({{ site.repo_url }}/blob/main/beanquery-demo/demo.http) (IntelliJ) and
[`demo.curl.sh`]({{ site.repo_url }}/blob/main/beanquery-demo/demo.curl.sh) (curl + jq).
The H2 console is available at `/h2-console`.
